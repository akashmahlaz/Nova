-- OTP state moved to Redis (cooldowns, hourly caps, email code hashes with TTL).
-- Phone codes are generated and checked by Twilio Verify v2. Postgres keeps only accounts and sessions.
DROP TABLE IF EXISTS otp_codes;
