-- Development seed: one service area (Auckland) with sample price cards.
-- NOT a migration — prices are business data. Run manually:
--   psql "$DATABASE_URL" -f server/seeds/dev_service_areas.sql
--
-- To test from another city, copy the block below with a new id, name, region, currency,
-- timezone and a bounding box: ST_MakeEnvelope(min_lng, min_lat, max_lng, max_lat, 4326).
-- In production, areas are real city boundaries (GeoJSON -> ST_GeomFromGeoJSON), not boxes.

INSERT INTO service_areas (id, name, region_code, currency, timezone, boundary)
VALUES (
    '01937a00-0000-7000-8000-000000000001',
    'Auckland',
    'NZ',
    'NZD',
    'Pacific/Auckland',
    ST_Multi(ST_MakeEnvelope(174.55, -37.10, 175.10, -36.60, 4326))::geography
)
ON CONFLICT (id) DO NOTHING;

-- Sample price cards (cents). Tune these to your market before launch.
INSERT INTO fare_rates (service_area_id, product_id, base_cents, per_km_cents, per_min_cents, minimum_cents, booking_fee_cents)
VALUES
    ('01937a00-0000-7000-8000-000000000001', 'ev',      250, 170, 40,  900, 150),
    ('01937a00-0000-7000-8000-000000000001', 'go',      270, 180, 42,  950, 150),
    ('01937a00-0000-7000-8000-000000000001', 'comfort', 350, 220, 50, 1200, 150),
    ('01937a00-0000-7000-8000-000000000001', 'xl',      450, 280, 60, 1500, 150)
ON CONFLICT (service_area_id, product_id) DO NOTHING;

-- Development area for testing from Punjab, India (state bounding box). Sample INR prices in paise,
-- rounded to whole rupees (100 paise) as Indian ride apps show them.
-- Remove with: DELETE FROM service_areas WHERE id = '01937a00-0000-7000-8000-000000000002';
INSERT INTO service_areas (id, name, region_code, currency, timezone, boundary)
VALUES (
    '01937a00-0000-7000-8000-000000000002',
    'Punjab (dev)',
    'IN',
    'INR',
    'Asia/Kolkata',
    ST_Multi(ST_MakeEnvelope(73.85, 29.50, 76.95, 32.55, 4326))::geography
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fare_rates (service_area_id, product_id, base_cents, per_km_cents, per_min_cents, minimum_cents, booking_fee_cents)
VALUES
    ('01937a00-0000-7000-8000-000000000002', 'ev',      3000, 1100, 150, 6000, 1000),
    ('01937a00-0000-7000-8000-000000000002', 'go',      3000, 1200, 150, 6000, 1000),
    ('01937a00-0000-7000-8000-000000000002', 'comfort', 4000, 1600, 200, 8000, 1000),
    ('01937a00-0000-7000-8000-000000000002', 'xl',      5000, 2000, 250, 10000, 1000)
ON CONFLICT (service_area_id, product_id) DO NOTHING;

-- Rounding per market (migration 0011). Safe to re-run.
UPDATE service_areas SET fare_rounding_minor = 100 WHERE id = '01937a00-0000-7000-8000-000000000002';
