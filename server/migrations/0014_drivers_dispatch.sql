-- Drivers, their vehicles, and the dispatch log.
--
-- A driver is a normal Nova account (same phone/email sign-in) plus a `drivers` row. Riding and
-- driving are separate capabilities, so `users.role` is left alone (an admin can also drive).
--
-- Ride status meaning once a driver is involved:
--   requested   -> dispatch is offering the ride to drivers, one at a time
--   accepted    -> a driver accepted and is heading to the pickup
--   arriving    -> the driver is at the pickup, waiting for the rider
--   in_progress -> rider on board
--   completed   -> dropped off
-- Additive only: no existing column changes.

CREATE TABLE drivers (
    user_id         UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    -- pending: applied, waiting for review. approved: can go online. suspended: blocked by an admin.
    status          TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'suspended')),
    licence_number  TEXT NOT NULL,
    licence_expiry  DATE,
    approved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Vehicle rows are never edited in place: changing car makes a new row and retires the old one,
-- so past rides keep pointing at the car that actually did the trip (plate on receipts).
CREATE TABLE vehicles (
    id                    UUID PRIMARY KEY,
    driver_id             UUID NOT NULL REFERENCES drivers (user_id) ON DELETE CASCADE,
    make                  TEXT NOT NULL,
    model                 TEXT NOT NULL,
    color                 TEXT NOT NULL,
    plate                 TEXT NOT NULL,
    year                  INT  NOT NULL CHECK (year BETWEEN 1990 AND 2100),
    seats                 INT  NOT NULL CHECK (seats BETWEEN 1 AND 8),
    is_electric           BOOLEAN NOT NULL DEFAULT false,
    wheelchair_accessible BOOLEAN NOT NULL DEFAULT false,
    active                BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    retired_at            TIMESTAMPTZ
);
-- One car in use per driver, and a plate can be on only one active car.
CREATE UNIQUE INDEX vehicles_one_active_per_driver ON vehicles (driver_id) WHERE active;
CREATE UNIQUE INDEX vehicles_active_plate ON vehicles (upper(plate)) WHERE active;

-- What a car needs for each ride type, as data (a new product is a row, not a code change).
-- Seats come from ride_products.seats.
ALTER TABLE ride_products
    ADD COLUMN requires_electric     BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN max_vehicle_age_years INT CHECK (max_vehicle_age_years > 0);
UPDATE ride_products SET requires_electric = true WHERE id = 'ev';
UPDATE ride_products SET max_vehicle_age_years = 7 WHERE id = 'comfort';

-- Ride types a driver takes. Filled from what the active vehicle qualifies for; a driver can
-- switch a type off (e.g. an XL driver who doesn't want Go trips).
CREATE TABLE driver_products (
    driver_id  UUID NOT NULL REFERENCES drivers (user_id) ON DELETE CASCADE,
    product_id TEXT NOT NULL REFERENCES ride_products (id),
    enabled    BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (driver_id, product_id)
);

-- Every offer dispatch made: who got it, how far away they were, and what they did.
-- Doubles as the "already asked" list so a driver isn't offered the same ride twice.
CREATE TABLE ride_offers (
    id              UUID PRIMARY KEY,
    ride_id         UUID NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    driver_id       UUID NOT NULL REFERENCES drivers (user_id) ON DELETE CASCADE,
    distance_meters INT  NOT NULL,
    offered_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    -- NULL while the driver is deciding.
    response        TEXT CHECK (response IN ('accepted', 'declined', 'expired', 'withdrawn')),
    responded_at    TIMESTAMPTZ,
    UNIQUE (ride_id, driver_id)
);
CREATE INDEX ride_offers_driver_open ON ride_offers (driver_id) WHERE response IS NULL;
CREATE INDEX ride_offers_driver_recent ON ride_offers (driver_id, offered_at DESC);

ALTER TABLE rides
    ADD COLUMN driver_id    UUID REFERENCES drivers (user_id) ON DELETE SET NULL,
    ADD COLUMN vehicle_id   UUID REFERENCES vehicles (id) ON DELETE SET NULL,
    ADD COLUMN accepted_at  TIMESTAMPTZ,
    ADD COLUMN arrived_at   TIMESTAMPTZ,
    ADD COLUMN started_at   TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ,
    -- 4-digit code the rider tells the driver at pickup; the driver can't start the trip without
    -- it, so nobody gets into the wrong car. NULL on rides booked before this existed.
    ADD COLUMN trip_pin     TEXT CHECK (trip_pin ~ '^[0-9]{4}$');

-- A driver works one ride at a time; this makes a double accept impossible, not just unlikely.
CREATE UNIQUE INDEX rides_one_active_per_driver ON rides (driver_id)
    WHERE status IN ('accepted', 'arriving', 'in_progress');
CREATE INDEX rides_driver_recent ON rides (driver_id, created_at DESC) WHERE driver_id IS NOT NULL;
