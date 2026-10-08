-- Store the moment, not a wall-clock reading: timestamptz instead of timestamp without time zone.
-- Existing values were written by the services in Docker, whose clock runs in UTC, so they are
-- read as UTC. The API then sends ISO-8601 UTC ("...Z") and browsers show local time.
ALTER TABLE payments ALTER COLUMN payment_date TYPE timestamp(6) with time zone USING payment_date AT TIME ZONE 'UTC';
