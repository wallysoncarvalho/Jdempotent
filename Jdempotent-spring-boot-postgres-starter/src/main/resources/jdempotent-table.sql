-- SQL script to create the jdempotent table for PostgreSQL
-- This table stores idempotent request-response data with TTL support

CREATE TABLE IF NOT EXISTS jdempotent (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    cache_prefix VARCHAR(255),
    request_data BYTEA,
    response_data BYTEA,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

-- Index for efficient cleanup of expired records
CREATE INDEX IF NOT EXISTS idx_jdempotent_expires_at ON jdempotent(expires_at);

-- Optional: Create a function to clean up expired records in batches
-- This can be called periodically by a scheduled job or external service
-- Uses SELECT FOR UPDATE SKIP LOCKED for concurrent-safe batch processing
CREATE OR REPLACE FUNCTION cleanup_expired_jdempotent_records(batch_size INTEGER DEFAULT 50)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    expired_keys TEXT[];
BEGIN
    -- Select expired records for deletion, skipping locked rows
    -- This allows multiple concurrent cleanup processes to work in parallel
    SELECT ARRAY(
        SELECT idempotency_key 
        FROM jdempotent 
        WHERE expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP
        ORDER BY expires_at ASC  -- Delete oldest expired records first
        LIMIT batch_size
        FOR UPDATE SKIP LOCKED   -- Skip rows locked by other transactions
    ) INTO expired_keys;
    
    -- Delete the selected records
    DELETE FROM jdempotent 
    WHERE idempotency_key = ANY(expired_keys);
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Example usage of the cleanup function:
-- SELECT cleanup_expired_jdempotent_records();           -- Delete up to 1000 records (default)
-- SELECT cleanup_expired_jdempotent_records(100);        -- Delete up to 100 records
-- SELECT cleanup_expired_jdempotent_records(5000);       -- Delete up to 5000 records

-- For complete cleanup in batches (run until no more expired records):
-- DO $$
-- DECLARE
--     deleted INTEGER;
-- BEGIN
--     LOOP
--         SELECT cleanup_expired_jdempotent_records(1000) INTO deleted;
--         EXIT WHEN deleted = 0;
--         RAISE NOTICE 'Deleted % expired records', deleted;
--     END LOOP;
-- END $$;
