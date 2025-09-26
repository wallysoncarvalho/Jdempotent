-- SQL script to create the jdempotent table for PostgreSQL
-- This table stores idempotent request-response data with TTL support

CREATE TABLE IF NOT EXISTS jdempotent (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    request_data BYTEA,
    response_data BYTEA,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

-- Index for efficient cleanup of expired records
CREATE INDEX IF NOT EXISTS idx_jdempotent_expires_at ON jdempotent(expires_at);

-- Optional: Create a function to clean up expired records
-- This can be called periodically by a scheduled job
CREATE OR REPLACE FUNCTION cleanup_expired_jdempotent_records()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM jdempotent WHERE expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Example usage of the cleanup function:
-- SELECT cleanup_expired_jdempotent_records();
