-- Add user role ('rider', 'driver', 'admin') for role-based access and Admin Web Dashboard.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'rider'
    CHECK (role IN ('rider', 'driver', 'admin'));
