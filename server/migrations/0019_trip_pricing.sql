-- Uber-style trip pricing and trip checks. Additive only: no column is dropped or retyped.
--
-- How a fare works (Uber's published model):
--   * Rate card per service area and ride type (fare_rates): base, per km, per minute, booking fee,
--     minimum fare, wait-time rate, cancellation fee. Rows are data, so a new city is a row.
--   * Upfront price: computed from the traffic-aware route before booking and locked in a quote.
--   * The upfront price holds unless the trip changes significantly (much longer or slower than
--     the route, or it ends away from the destination). Then the fare is recomputed on the actual
--     time and distance at the same rates and surge (rides.fare_basis = 'actual').
--   * Wait time: after a grace period from the driver's GPS arrival, each minute is charged.
--   * Rider cancellation fee: after a grace period from acceptance, if the driver was making
--     progress. No-show fee: the driver waited long enough at the pickup.
--   * Long pickup: when the drive to the pickup runs past a threshold, the platform (not the
--     rider) pays the driver time and distance for the extra part.
--   * The driver earns the fare minus the booking fee minus the area's service fee, plus long
--     pickup pay. The rider pays the fare.

ALTER TABLE fare_rates
    ADD COLUMN wait_per_min_cents     INT NOT NULL DEFAULT 0   CHECK (wait_per_min_cents >= 0),
    ADD COLUMN wait_grace_secs        INT NOT NULL DEFAULT 120 CHECK (wait_grace_secs >= 0),
    ADD COLUMN cancel_fee_cents       INT NOT NULL DEFAULT 0   CHECK (cancel_fee_cents >= 0),
    ADD COLUMN cancel_grace_secs      INT NOT NULL DEFAULT 180 CHECK (cancel_grace_secs >= 0),
    ADD COLUMN no_show_wait_secs      INT NOT NULL DEFAULT 300 CHECK (no_show_wait_secs >= 0),
    ADD COLUMN long_pickup_after_secs INT NOT NULL DEFAULT 600 CHECK (long_pickup_after_secs > 0),
    ADD COLUMN updated_at             TIMESTAMPTZ NOT NULL DEFAULT now();

-- Existing cards: Uber's wait rate is the card's per-minute rate, and its cancellation fee is in
-- the range of the minimum fare. Derived from each card, so no currency amount is typed here.
UPDATE fare_rates SET wait_per_min_cents = per_min_cents, cancel_fee_cents = minimum_cents;

-- Market rules per service area (legal limits and platform terms), not prices.
ALTER TABLE service_areas
    -- Highest surge multiplier allowed here.
    ADD COLUMN max_surge        DOUBLE PRECISION NOT NULL DEFAULT 3 CHECK (max_surge >= 1 AND max_surge <= 5),
    -- Platform share of the trip fare in basis points (2000 = 20%); the driver keeps the rest.
    ADD COLUMN service_fee_bps  INT NOT NULL DEFAULT 2000 CHECK (service_fee_bps BETWEEN 0 AND 10000),
    -- Cancellation and no-show fees can't exceed this share of the upfront fare (10000 = no limit).
    ADD COLUMN cancel_fee_max_bps INT NOT NULL DEFAULT 10000 CHECK (cancel_fee_max_bps BETWEEN 0 AND 10000),
    -- "At the pickup" for Arrived and no-show: Uber treats a driver within 0.2 mi as arriving.
    ADD COLUMN arrive_radius_m  INT NOT NULL DEFAULT 320 CHECK (arrive_radius_m > 0),
    -- Ending further than this from the destination reprices the trip on actual time and distance.
    ADD COLUMN dropoff_radius_m INT NOT NULL DEFAULT 500 CHECK (dropoff_radius_m > 0),
    -- A trip this much longer (distance) or slower (time) than its route is repriced on actuals.
    ADD COLUMN reprice_over_pct INT NOT NULL DEFAULT 25 CHECK (reprice_over_pct > 0);

-- India, Motor Vehicle Aggregator Guidelines 2025: surge at most 2x, driver keeps at least 80%,
-- cancellation fee at most 10% of the fare.
UPDATE service_areas SET max_surge = 2, service_fee_bps = 2000, cancel_fee_max_bps = 1000 WHERE region_code = 'IN';

-- The rate card a ride was booked on, copied at booking so later card edits never change a
-- ride already booked, and so the final fare can be recomputed on the same rates.
CREATE TABLE ride_rate_cards (
    ride_id                UUID PRIMARY KEY REFERENCES rides (id) ON DELETE CASCADE,
    base_cents             INT NOT NULL,
    per_km_cents           INT NOT NULL,
    per_min_cents          INT NOT NULL,
    minimum_cents          INT NOT NULL,
    booking_fee_cents      INT NOT NULL,
    wait_per_min_cents     INT NOT NULL,
    wait_grace_secs        INT NOT NULL,
    cancel_fee_cents       INT NOT NULL,
    cancel_grace_secs      INT NOT NULL,
    no_show_wait_secs      INT NOT NULL,
    long_pickup_after_secs INT NOT NULL,
    rounding_minor         INT NOT NULL,
    service_fee_bps        INT NOT NULL,
    cancel_fee_max_bps     INT NOT NULL,
    reprice_over_pct       INT NOT NULL,
    dropoff_radius_m       INT NOT NULL,
    arrive_radius_m        INT NOT NULL
);

-- The driver's GPS trail for a ride, from acceptance to drop-off. Gives the real distance driven
-- (ST_Length of the line through the points) and is the evidence for fare disputes.
CREATE TABLE ride_track_points (
    ride_id     UUID NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    phase       TEXT NOT NULL CHECK (phase IN ('to_pickup', 'on_trip')),
    location    geography(Point, 4326) NOT NULL,
    PRIMARY KEY (ride_id, recorded_at)
);

ALTER TABLE rides
    -- What the rider was shown before booking. fare_cents is what they pay in the end.
    ADD COLUMN upfront_fare_cents      BIGINT,
    ADD COLUMN fare_basis              TEXT NOT NULL DEFAULT 'upfront' CHECK (fare_basis IN ('upfront', 'actual')),
    ADD COLUMN fare_change_reason      TEXT CHECK (fare_change_reason IN ('longer_trip', 'ended_away')),
    ADD COLUMN actual_distance_meters  BIGINT,
    ADD COLUMN actual_duration_seconds BIGINT,
    ADD COLUMN wait_seconds            BIGINT,
    ADD COLUMN wait_fee_cents          BIGINT NOT NULL DEFAULT 0 CHECK (wait_fee_cents >= 0),
    ADD COLUMN cancel_fee_cents        BIGINT NOT NULL DEFAULT 0 CHECK (cancel_fee_cents >= 0),
    ADD COLUMN long_pickup_cents       BIGINT NOT NULL DEFAULT 0 CHECK (long_pickup_cents >= 0),
    ADD COLUMN service_fee_cents       BIGINT,
    ADD COLUMN driver_earnings_cents   BIGINT,
    -- How far the driver was from the pickup when they tapped Arrived, and from the destination
    -- when they ended the trip, by GPS.
    ADD COLUMN arrived_distance_m      INT,
    ADD COLUMN dropoff_distance_m      INT,
    ADD COLUMN ended_at_point          geography(Point, 4326);

UPDATE rides SET upfront_fare_cents = fare_cents;
