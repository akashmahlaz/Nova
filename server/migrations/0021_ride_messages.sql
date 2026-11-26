-- In-trip chat between rider and driver (Uber's in-app chat), so neither side needs the other's
-- phone number to message. Open while a driver is assigned; kept afterwards for support.
CREATE TABLE ride_messages (
    id          UUID PRIMARY KEY,
    ride_id     UUID NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    sender_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sender_role TEXT NOT NULL CHECK (sender_role IN ('rider', 'driver')),
    body        TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);
CREATE INDEX ride_messages_ride ON ride_messages (ride_id, created_at);
