-- Persist the driver's chosen city, country, and commercial passenger licence flag.
ALTER TABLE drivers ADD COLUMN city TEXT;
ALTER TABLE drivers ADD COLUMN country_code TEXT;
ALTER TABLE drivers ADD COLUMN has_passenger_licence BOOLEAN NOT NULL DEFAULT false;
