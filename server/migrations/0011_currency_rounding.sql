-- Price rounding is market data, not code. Fares are computed in the currency's minor unit
-- (ISO 4217: cents, paise, yen...) and rounded to this many minor units, so each service area can
-- match local pricing habits: 10 = NZ$24.60, 100 = ₹245 (whole rupees), 1 = no rounding.
ALTER TABLE service_areas
    ADD COLUMN fare_rounding_minor INT NOT NULL DEFAULT 10 CHECK (fare_rounding_minor > 0);

-- Currency codes are ISO 4217 alphabetic codes (three capital letters), checked at the source.
ALTER TABLE service_areas
    ADD CONSTRAINT service_areas_currency_iso4217 CHECK (currency ~ '^[A-Z]{3}$');
