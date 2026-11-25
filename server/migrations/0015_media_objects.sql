-- Signed S3 media & document metadata.
-- Objects live in a private S3 bucket; this table tracks ownership, verification status,
-- and audit trail so presigned GET/PUT URLs are always gated by PostgreSQL authorization.

CREATE TABLE media_objects (
    id               UUID PRIMARY KEY,
    owner_user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    bucket           TEXT NOT NULL,
    object_key       TEXT NOT NULL UNIQUE,
    category         TEXT NOT NULL CHECK (category IN (
                        'avatar', 'driver_licence', 'driver_insurance',
                        'vehicle_reg', 'support_attachment', 'admin_asset'
                     )),
    file_name        TEXT NOT NULL DEFAULT '',
    content_type     TEXT NOT NULL,
    size_bytes       BIGINT NOT NULL DEFAULT 0 CHECK (size_bytes >= 0),
    status           TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'uploaded', 'verified', 'rejected')),
    reviewed_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX media_objects_owner ON media_objects (owner_user_id, created_at DESC);
CREATE INDEX media_objects_status ON media_objects (status, created_at DESC);
