-- Nova rider/driver auth: accounts, one-time codes, refresh tokens.

CREATE TABLE users (
    id                UUID PRIMARY KEY,
    phone_e164        TEXT UNIQUE,
    email             TEXT UNIQUE,
    first_name        TEXT NOT NULL,
    last_name         TEXT NOT NULL DEFAULT '',
    phone_verified_at TIMESTAMPTZ,
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_has_identifier CHECK (phone_e164 IS NOT NULL OR email IS NOT NULL)
);

-- Hashed OTPs only; the plain code is never stored.
CREATE TABLE otp_codes (
    id          UUID PRIMARY KEY,
    channel     TEXT NOT NULL CHECK (channel IN ('phone', 'email')),
    destination TEXT NOT NULL,
    purpose     TEXT NOT NULL CHECK (purpose IN ('signup', 'login')),
    code_hash   TEXT NOT NULL,
    attempts    INT NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX otp_codes_lookup ON otp_codes (channel, destination, purpose, created_at DESC);

-- Refresh tokens are opaque random strings; only their SHA-256 is stored. Rotated on every use.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user ON refresh_tokens (user_id);
