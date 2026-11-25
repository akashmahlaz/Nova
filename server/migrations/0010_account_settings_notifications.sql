-- Rider preferences, in-app notifications and account deletion.

-- One row per rider, created on first save. Missing row = defaults.
CREATE TABLE rider_settings (
    user_id            UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    -- Dispatch only offers wheelchair-accessible vehicles when set (used by matching).
    wheelchair_access  BOOLEAN NOT NULL DEFAULT false,
    -- Shown to the driver with the request.
    quiet_ride         BOOLEAN NOT NULL DEFAULT false,
    -- The app stops reading location after drop-off when set.
    trip_location_only BOOLEAN NOT NULL DEFAULT true,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- In-app inbox. Written by the server when something happens to the rider's rides or account.
CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       TEXT NOT NULL CHECK (kind IN ('trip', 'offer', 'account')),
    -- What it is about, so the app can pick an icon: ride_scheduled, ride_cancelled, no_drivers, ...
    event      TEXT NOT NULL,
    title      TEXT NOT NULL,
    body       TEXT NOT NULL DEFAULT '',
    ride_id    UUID REFERENCES rides (id) ON DELETE SET NULL,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Inbox page (newest first, keyset) and the unread badge count.
CREATE INDEX notifications_user_recent ON notifications (user_id, created_at DESC);
CREATE INDEX notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

-- Account deletion with a 30-day grace period: signing in again cancels it; after 30 days a
-- background task deletes the user (and, by ON DELETE CASCADE, their data).
ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMPTZ;
CREATE INDEX users_pending_deletion ON users (deletion_requested_at) WHERE deletion_requested_at IS NOT NULL;
