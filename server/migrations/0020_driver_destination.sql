-- Destination filter (Uber's "set your destination"): a driver heading somewhere gets only
-- trips that end closer to it. Limited per day so it can't be used to cherry-pick every trip.
-- Additive only.

ALTER TABLE drivers
    ADD COLUMN destination      geography(Point, 4326),
    ADD COLUMN destination_name TEXT,
    ADD COLUMN destination_set_at TIMESTAMPTZ;

-- Every time a driver sets a destination, for the daily limit and for support.
CREATE TABLE driver_destination_uses (
    id        UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES drivers (user_id) ON DELETE CASCADE,
    name      TEXT NOT NULL,
    location  geography(Point, 4326) NOT NULL,
    used_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX driver_destination_uses_recent ON driver_destination_uses (driver_id, used_at DESC);

-- How many times a day a driver may set a destination, per area (Uber: 2).
ALTER TABLE service_areas
    ADD COLUMN destination_uses_per_day INT NOT NULL DEFAULT 2 CHECK (destination_uses_per_day >= 0);
