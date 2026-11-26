-- Driver onboarding, for real: required documents (photos in S3 via media_objects), payout
-- details, and online shifts (for "hours online" in earnings). Tables only, no JSONB.

-- Two more document kinds drivers upload.
ALTER TABLE media_objects DROP CONSTRAINT media_objects_category_check;
ALTER TABLE media_objects ADD CONSTRAINT media_objects_category_check CHECK (category IN (
    'avatar', 'driver_licence', 'driver_endorsement', 'driver_photo', 'driver_insurance',
    'vehicle_reg', 'support_attachment', 'admin_asset'
));
-- "Latest document of each kind for this driver" is the hot lookup.
CREATE INDEX media_objects_owner_category ON media_objects (owner_user_id, category, created_at DESC);

-- Which documents a driver must provide, as data: a new requirement is a row, not an app update.
CREATE TABLE driver_document_types (
    category    TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    required    BOOLEAN NOT NULL DEFAULT true,
    sort_order  INT NOT NULL DEFAULT 0
);
INSERT INTO driver_document_types (category, name, description, sort_order) VALUES
    ('driver_licence',     'Driving licence',   'Photo of the front of your licence',      0),
    ('driver_endorsement', 'P endorsement',     'Your passenger endorsement (P)',          1),
    ('driver_photo',       'Profile photo',     'A clear photo of your face, no sunglasses', 2),
    ('vehicle_reg',        'Vehicle rego',      'Registration and WoF/CoF for your car',   3),
    ('driver_insurance',   'Vehicle insurance', 'Policy showing commercial passenger use', 4);

-- Where earnings are paid. Kept apart from `drivers` so it can be filled before applying.
-- Only the driver and admins can read it (see drivers.rs / admin.rs).
CREATE TABLE driver_payout_accounts (
    user_id         UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    account_name    TEXT NOT NULL,
    account_number  TEXT NOT NULL,
    tax_number      TEXT NOT NULL DEFAULT '',
    gst_registered  BOOLEAN NOT NULL DEFAULT false,
    payout_schedule TEXT NOT NULL DEFAULT 'daily' CHECK (payout_schedule IN ('daily', 'weekly', 'instant')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Review trail on the application itself.
ALTER TABLE drivers
    ADD COLUMN submitted_at TIMESTAMPTZ,
    ADD COLUMN review_note  TEXT;

-- One row per stretch of time online. An open shift whose app stopped reporting is closed at
-- its last_seen_at, so a crashed phone doesn't count as hours online.
CREATE TABLE driver_shifts (
    id           UUID PRIMARY KEY,
    driver_id    UUID NOT NULL REFERENCES drivers (user_id) ON DELETE CASCADE,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at     TIMESTAMPTZ
);
CREATE INDEX driver_shifts_driver_recent ON driver_shifts (driver_id, started_at DESC);
CREATE UNIQUE INDEX driver_shifts_one_open ON driver_shifts (driver_id) WHERE ended_at IS NULL;
