-- Phase 2: geography, service areas and upfront pricing.
--
-- Design notes (see docs in server/src/geo/mod.rs):
--   * `geography` (not `geometry`) so distances/containment are computed on the spheroid in metres,
--     which is what a ride-hailing app means by "within 5 km". Indexed with GiST.
--   * Money is always integer minor units (cents) + an ISO 4217 currency on the service area.
--     Never floats for money.
--   * Prices are per service area, so a new city is data (a row), not a code change.

CREATE EXTENSION IF NOT EXISTS postgis;

-- A city / operating zone. Pickups must fall inside an active area to get a quote.
CREATE TABLE service_areas (
    id          UUID PRIMARY KEY,
    name        TEXT NOT NULL,
    -- CLDR region code (e.g. 'NZ'). Biases Places autocomplete to this country.
    region_code TEXT NOT NULL CHECK (region_code ~ '^[A-Z]{2}$'),
    -- ISO 4217 currency for every fare quoted in this area (e.g. 'NZD').
    currency    TEXT NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    -- IANA timezone, for scheduled rides and receipts later (e.g. 'Pacific/Auckland').
    timezone    TEXT NOT NULL,
    boundary    geography(MultiPolygon, 4326) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- GiST makes ST_Covers(boundary, point) an index lookup instead of a scan over every polygon.
CREATE INDEX service_areas_boundary_gist ON service_areas USING GIST (boundary);

-- The ride types the app offers. Catalog data, shared by every area.
CREATE TABLE ride_products (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    seats       INT  NOT NULL CHECK (seats > 0),
    description TEXT NOT NULL DEFAULT '',
    sort_order  INT  NOT NULL DEFAULT 0
);

INSERT INTO ride_products (id, name, seats, description, sort_order) VALUES
    ('ev',      'Electric', 4, 'Zero-emission cars',  0),
    ('go',      'Go',       4, 'Everyday rides',      1),
    ('comfort', 'Comfort',  4, 'Top-rated drivers',   2),
    ('xl',      'XL',       6, 'Room for six',        3);

-- Price card per (area, product). Fare = base + per_km*km + per_min*min, x surge,
-- + booking fee, floored at minimum. All values in the area's currency minor units.
CREATE TABLE fare_rates (
    service_area_id   UUID NOT NULL REFERENCES service_areas (id) ON DELETE CASCADE,
    product_id        TEXT NOT NULL REFERENCES ride_products (id),
    base_cents        INT  NOT NULL CHECK (base_cents >= 0),
    per_km_cents      INT  NOT NULL CHECK (per_km_cents >= 0),
    per_min_cents     INT  NOT NULL CHECK (per_min_cents >= 0),
    minimum_cents     INT  NOT NULL CHECK (minimum_cents >= 0),
    booking_fee_cents INT  NOT NULL DEFAULT 0 CHECK (booking_fee_cents >= 0),
    active            BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (service_area_id, product_id)
);
