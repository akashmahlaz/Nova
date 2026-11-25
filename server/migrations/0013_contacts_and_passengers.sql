-- "Who's riding?" contact matching and booking for another Nova user.

-- SHA-256 (hex) of the E.164 phone, computed by Postgres. The app sends hashes of its contacts'
-- numbers and we compare against this, so raw contact numbers never leave the phone and are
-- never stored. Indexed for "which of these 1000 hashes are Nova users".
-- convert_to() is only STABLE (it depends on server encoding settings), so generated columns
-- reject it. With the encoding fixed to UTF8 the result depends only on the input, so this
-- wrapper is safely IMMUTABLE; it must match the app's SHA-256 of the UTF-8 E.164 string.
CREATE FUNCTION nova_phone_sha256(phone TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$ SELECT encode(sha256(convert_to(phone, 'UTF8')), 'hex') $$;

ALTER TABLE users
    ADD COLUMN phone_sha256 TEXT GENERATED ALWAYS AS (nova_phone_sha256(phone_e164)) STORED;
CREATE INDEX users_phone_sha256 ON users (phone_sha256) WHERE phone_verified_at IS NOT NULL;

-- When the person booked for has a Nova account, the ride is linked to them: they get the
-- in-app notification now and live updates (WebSocket) later. NULL = not on Nova.
ALTER TABLE rides ADD COLUMN passenger_user_id UUID REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX rides_passenger_user ON rides (passenger_user_id, created_at DESC) WHERE passenger_user_id IS NOT NULL;
