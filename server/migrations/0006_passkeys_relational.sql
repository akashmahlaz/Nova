-- Replace JSONB blob in passkey_credentials with 100% normalized relational columns.

DROP TABLE IF EXISTS passkey_credentials;

CREATE TABLE passkey_credentials (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    credential_id       TEXT NOT NULL UNIQUE,
    cred_id_raw         BYTEA NOT NULL,
    public_key_cbor     BYTEA NOT NULL,
    counter             BIGINT NOT NULL DEFAULT 0,
    user_verified       BOOLEAN NOT NULL DEFAULT true,
    backup_eligible     BOOLEAN NOT NULL DEFAULT false,
    backup_state        BOOLEAN NOT NULL DEFAULT false,
    registration_policy TEXT NOT NULL DEFAULT 'required',
    attestation_format  TEXT NOT NULL DEFAULT 'none',
    transports          TEXT[],
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ
);

CREATE INDEX passkey_credentials_user ON passkey_credentials (user_id);
