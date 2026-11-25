-- Normalize ride stops out of rides.stops JSONB into a relational table,
-- and add share_token for passenger tracking App Links.

CREATE TABLE ride_stops (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id     UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
    stop_order  SMALLINT NOT NULL CHECK (stop_order >= 0 AND stop_order < 3),
    name        TEXT NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    UNIQUE (ride_id, stop_order)
);

CREATE INDEX idx_ride_stops_ride ON ride_stops (ride_id, stop_order);

-- rides.stops was empty in every database when this ran (checked), so there is nothing to copy:
-- the old column is dropped and stops live only in ride_stops from here on. Nova stores data in
-- tables, never JSON/JSONB columns.
ALTER TABLE rides DROP COLUMN stops;

-- URL-safe token for passenger live-tracking links (/track/<token>).
ALTER TABLE rides ADD COLUMN share_token TEXT;
UPDATE rides SET share_token = replace(gen_random_uuid()::text, '-', '') WHERE share_token IS NULL;
ALTER TABLE rides ALTER COLUMN share_token SET NOT NULL;
ALTER TABLE rides ADD CONSTRAINT rides_share_token_key UNIQUE (share_token);
