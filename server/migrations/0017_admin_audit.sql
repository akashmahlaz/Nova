-- Professional admin actions: accounts are deactivated, never hard-deleted, and every admin
-- action leaves a trail. Additive only. Tables only, no JSONB.

-- A deactivated account keeps its history (rides, documents, payouts) but can't sign in.
ALTER TABLE users
    ADD COLUMN deactivated_at     TIMESTAMPTZ,
    ADD COLUMN deactivated_reason TEXT;

-- Who did what, to which record, and why. Written by the server only; never edited or deleted
-- from the console.
CREATE TABLE admin_audit_log (
    id          UUID PRIMARY KEY,
    admin_id    UUID NOT NULL REFERENCES users (id),
    action      TEXT NOT NULL,
    target_type TEXT NOT NULL CHECK (target_type IN ('user', 'driver', 'media', 'service_area', 'ride')),
    target_id   UUID NOT NULL,
    -- Human sentence shown in the activity feed, e.g. "Approved Aroha Ngata as a driver".
    summary     TEXT NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX admin_audit_recent ON admin_audit_log (created_at DESC);
CREATE INDEX admin_audit_target ON admin_audit_log (target_type, target_id, created_at DESC);
