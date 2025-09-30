package jdempotent.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.trendyol.jdempotent.postgres.JdempotentPostgresCleanupService;
import com.trendyol.jdempotent.postgres.JdempotentPostgresProperties;

import jdempotent.postgres.config.TestPostgresConfig;
import jdempotent.postgres.support.AbstractPostgresStarterIntegrationTest;

/**
 * Integration tests for the Jdempotent PostgreSQL scheduler functionality.
 * 
 * Tests the complete integration including database operations, scheduling,
 * and cleanup functionality using a real PostgreSQL database via Testcontainers.
 */
//@SpringBootTest(classes = JdempotentPostgresSchedulerIntegrationTest.TestApplication.class)
@ExtendWith(SpringExtension.class)
@Import(TestPostgresConfig.class)
class JdempotentPostgresSchedulerIntegrationTest extends AbstractPostgresStarterIntegrationTest {

    @Autowired
    private JdempotentPostgresCleanupService cleanupService;

    @Autowired
    private JdempotentPostgresProperties postgresProperties;

    @BeforeEach 
    void setUp() {
        postgresProperties.getScheduler().setBatchSize(50);
    }
    
    @Test
    void test_scheduler_auto_configuration_loads() {
        assertNotNull(cleanupService);
        assertNotNull(postgresProperties);
        
        assertTrue(postgresProperties.getScheduler().isEnabled());
        assertEquals(50, postgresProperties.getScheduler().getBatchSize());
    }

    @Test
    void test_database_setup_and_cleanup_function() {
        // Verify the cleanup function exists
        Integer functionExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_proc WHERE proname = 'cleanup_expired_jdempotent_records'",
            Integer.class
        );
        assertEquals(1, functionExists);
        
        // Test the cleanup function directly
        Integer deletedCount = jdbcTemplate.queryForObject(
            "SELECT cleanup_expired_jdempotent_records(10)",
            Integer.class
        );
        assertNotNull(deletedCount);
        assertEquals(0, deletedCount); // No expired records initially
    }

    @Test
    void test_cleanup_service_perform_cleanup() {
        // Insert some test data with expired records
        insertTestData();
        
        // Verify we have expired records
        Integer expiredCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM jdempotent WHERE expires_at < CURRENT_TIMESTAMP",
            Integer.class
        );
        assertTrue(expiredCount > 0);
        
        // Perform cleanup
        int deletedCount = cleanupService.performCleanup();
        
        // Verify records were deleted
        assertTrue(deletedCount > 0);
        
        // Verify expired records are gone
        Integer remainingExpired = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM jdempotent WHERE expires_at < CURRENT_TIMESTAMP",
            Integer.class
        );
        assertEquals(0, remainingExpired);
    }

    @Test
    void test_cleanup_with_large_batch_size() {
        // Insert many expired records
        for (int i = 0; i < 200; i++) {
            jdbcTemplate.update(
                "INSERT INTO jdempotent (idempotency_key, expires_at) VALUES (?, CURRENT_TIMESTAMP - INTERVAL '1 hour')",
                "expired-key-" + i
            );
        }
        
        // Set large batch size
        postgresProperties.getScheduler().setBatchSize(150);
        
        // Verify we have 200 expired records
        Integer expiredCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM jdempotent WHERE expires_at < CURRENT_TIMESTAMP",
            Integer.class
        );
        assertEquals(200, expiredCount);
        
        // Perform cleanup
        int deletedCount = cleanupService.performCleanup();
        
        // Should delete up to batch size (150)
        assertEquals(150, deletedCount);
        
        // Should have 50 remaining
        Integer remainingCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM jdempotent WHERE expires_at < CURRENT_TIMESTAMP",
            Integer.class
        );
        assertEquals(50, remainingCount);
    }

    private void insertTestData() {
        // Insert some expired records
        jdbcTemplate.update(
            "INSERT INTO jdempotent (idempotency_key, expires_at) VALUES (?, CURRENT_TIMESTAMP - INTERVAL '1 hour')",
            "expired-key-1"
        );
        jdbcTemplate.update(
            "INSERT INTO jdempotent (idempotency_key, expires_at) VALUES (?, CURRENT_TIMESTAMP - INTERVAL '2 hours')",
            "expired-key-2"
        );
        jdbcTemplate.update(
            "INSERT INTO jdempotent (idempotency_key, expires_at) VALUES (?, CURRENT_TIMESTAMP - INTERVAL '30 minutes')",
            "expired-key-3"
        );
        
        // Insert some non-expired records
        jdbcTemplate.update(
            "INSERT INTO jdempotent (idempotency_key, expires_at) VALUES (?, CURRENT_TIMESTAMP + INTERVAL '1 hour')",
            "valid-key-1"
        );
        jdbcTemplate.update(
            "INSERT INTO jdempotent (idempotency_key, expires_at) VALUES (?, NULL)",
            "permanent-key-1"
        );
    }
}
