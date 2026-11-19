-- Passkey (WebAuthn) credentials. One row per registered authenticator per user.
-- The full `Passkey` is stored as JSON so it can be reloaded for authentication; the
-- credential_id is kept as a separate indexed column for fast lookup by the login flow.

CREATE TABLE passkey_credentials (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    credential_id TEXT NOT NULL UNIQUE,
    passkey       JSONB NOT NULL,
    counter       BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ
);

CREATE INDEX passkey_credentials_user ON passkey_credentials (user_id);