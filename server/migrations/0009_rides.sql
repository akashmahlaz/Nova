-- Ride requests. A row is created when the rider books against a locked quote, so the fare,
-- route and currency here are exactly what the rider saw (never a price sent by the app).
--
-- Status flow:
--   scheduled -> requested -> accepted -> arriving -> in_progress -> completed
--   requested -> no_drivers            (nobody accepted in time)
--   any non-final -> cancelled
-- Driver assignment fields arrive with dispatch (driver app phase).

CREATE TABLE rides (
    id               UUID PRIMARY KEY,
    rider_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- One booking per quote: a double tap on "Book" can't create two rides.
    quote_id         UUID NOT NULL UNIQUE,
    service_area_id  UUID NOT NULL REFERENCES service_areas (id),
    product_id       TEXT NOT NULL REFERENCES ride_products (id),
    status           TEXT NOT NULL CHECK (status IN
                        ('scheduled', 'requested', 'accepted', 'arriving', 'in_progress',
                         'completed', 'cancelled', 'no_drivers')),

    pickup_name      TEXT NOT NULL,
    pickup_address   TEXT NOT NULL DEFAULT '',
    pickup_lat       DOUBLE PRECISION NOT NULL,
    pickup_lng       DOUBLE PRECISION NOT NULL,
    -- Same point as geography, GiST indexed: dispatch asks "requested rides near this driver".
    pickup           geography(Point, 4326) NOT NULL,
    dropoff_name     TEXT NOT NULL,
    dropoff_address  TEXT NOT NULL DEFAULT '',
    dropoff_lat      DOUBLE PRECISION NOT NULL,
    dropoff_lng      DOUBLE PRECISION NOT NULL,
    -- [{ "name", "lat", "lng" }] in order, at most 3.
    stops            JSONB NOT NULL DEFAULT '[]',

    currency         TEXT NOT NULL,
    fare_cents       BIGINT NOT NULL CHECK (fare_cents >= 0),
    surge_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1,
    distance_meters  BIGINT NOT NULL,
    duration_seconds BIGINT NOT NULL,
    polyline         TEXT NOT NULL DEFAULT '',
    payment_method   TEXT NOT NULL CHECK (payment_method IN ('gpay', 'card', 'credit')),

    -- "Book for someone else": who is actually riding. NULL = the rider themself.
    passenger_name   TEXT,
    passenger_phone  TEXT,

    scheduled_at     TIMESTAMPTZ,
    requested_at     TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    cancel_reason    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK (status <> 'scheduled' OR scheduled_at IS NOT NULL)
);

-- Activity list: a rider's rides, newest first.
CREATE INDEX rides_rider_recent ON rides (rider_id, created_at DESC);
-- A rider can have only one ride in progress at a time (scheduled rides don't count).
CREATE UNIQUE INDEX rides_one_active_per_rider ON rides (rider_id)
    WHERE status IN ('requested', 'accepted', 'arriving', 'in_progress');
-- Dispatch: open requests near a driver, and scheduled rides coming due.
CREATE INDEX rides_open_pickup ON rides USING GIST (pickup) WHERE status = 'requested';
CREATE INDEX rides_scheduled_due ON rides (scheduled_at) WHERE status = 'scheduled';
