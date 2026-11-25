-- Rider saved places (Home, Work, custom destinations) and emergency contacts.

CREATE TABLE saved_places (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       TEXT NOT NULL CHECK (kind IN ('home', 'work', 'custom')),
    label      TEXT NOT NULL,
    address    TEXT NOT NULL,
    lat        DOUBLE PRECISION,
    lng        DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX saved_places_user ON saved_places (user_id, created_at DESC);
-- Each user can have at most one 'home' and one 'work' slot.
CREATE UNIQUE INDEX saved_places_unique_slot
    ON saved_places (user_id, kind)
    WHERE kind IN ('home', 'work');

CREATE TABLE emergency_contacts (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    phone_e164   TEXT NOT NULL,
    relationship TEXT NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX emergency_contacts_user ON emergency_contacts (user_id, created_at ASC);
