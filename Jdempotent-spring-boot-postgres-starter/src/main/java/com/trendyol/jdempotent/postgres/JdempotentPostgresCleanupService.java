package com.trendyol.jdempotent.postgres;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;

/**
 * Service responsible for scheduled cleanup of expired records from the PostgreSQL idempotent table.
 * 
 * <p>This service uses the PostgreSQL stored function {@code cleanup_expired_jdempotent_records}
 * to efficiently remove expired records in batches. The cleanup operation is designed to be
 * concurrent-safe using SELECT FOR UPDATE SKIP LOCKED to avoid lock contention.</p>
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Configurable batch size for cleanup operations</li>
 *   <li>Comprehensive logging of cleanup results and errors</li>
 *   <li>Support for multiple scheduling strategies (fixed delay, fixed rate, cron)</li>
 *   <li>Concurrent-safe cleanup using PostgreSQL's SKIP LOCKED feature</li>
 *   <li>Graceful error handling with detailed error logging</li>
 * </ul>
 * 
 * <h3>Scheduling Configuration:</h3>
 * <p>The cleanup task can be scheduled using one of two methods:</p>
 * <ol>
 *   <li><strong>Fixed Rate:</strong> {@code @Scheduled(fixedRateString = "${jdempotent.postgres.scheduler.fixedRate}")}</li>
 *   <li><strong>Cron Expression:</strong> {@code @Scheduled(cron = "${jdempotent.postgres.scheduler.cron}")}</li>
 * </ol>
 * 
 * <h3>Database Function:</h3>
 * <p>This service relies on the PostgreSQL function {@code cleanup_expired_jdempotent_records(batch_size)}
 * which must be created using the provided SQL script. The function returns the number of deleted records.</p>
 * 
 * <h3>Logging:</h3>
 * <p>The service provides detailed logging at different levels:</p>
 * <ul>
 *   <li><strong>INFO:</strong> Successful cleanup operations with record counts</li>
 *   <li><strong>DEBUG:</strong> Detailed execution information</li>
 *   <li><strong>WARN:</strong> Non-critical issues (e.g., no records to clean)</li>
 *   <li><strong>ERROR:</strong> Critical failures with full stack traces</li>
 * </ul>
 * 
 * @author Wallyson Soares
 * @since 2.0.0
 * @see JdempotentPostgresProperties
 * @see JdempotentPostgresSchedulerAutoConfiguration
 */
@Service
public class JdempotentPostgresCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(JdempotentPostgresCleanupService.class);

    private final EntityManagerFactory entityManagerFactory;
    private final JdempotentPostgresProperties postgresProperties;

    /**
     * Constructs a new cleanup service with the required dependencies.
     * 
     * @param entityManagerFactory the EntityManagerFactory for database operations
     * @param postgresProperties the PostgreSQL configuration properties (including scheduler properties)
     */
    public JdempotentPostgresCleanupService(
            EntityManagerFactory entityManagerFactory,
            JdempotentPostgresProperties postgresProperties) {
        this.entityManagerFactory = entityManagerFactory;
        this.postgresProperties = postgresProperties;
    }

    /**
     * Performs the scheduled cleanup operation.
     * This method is called by the appropriate scheduler based on configuration.
     */
    public void executeScheduledCleanup() {
        logger.info("Starting scheduled cleanup task");
        try {
            performCleanup();
        } finally {
            logger.info("Completed scheduled cleanup task");
        }
    }

    /**
     * Performs the actual cleanup operation by calling the PostgreSQL cleanup function.
     * 
     * <p>This method executes the {@code cleanup_expired_jdempotent_records} function
     * with the configured batch size and logs the results. It handles all database
     * operations within a transaction for consistency.</p>
     * 
     * <h3>Operation Flow:</h3>
     * <ol>
     *   <li>Create EntityManager and begin transaction</li>
     *   <li>Call PostgreSQL cleanup function with batch size</li>
     *   <li>Log the number of deleted records</li>
     *   <li>Commit transaction</li>
     *   <li>Handle any errors with appropriate logging</li>
     * </ol>
     * 
     * @return the number of records deleted, or -1 if an error occurred
     */
    public int performCleanup() {
        long startTime = System.currentTimeMillis();
        EntityManager entityManager = null;
        
        try {
            entityManager = entityManagerFactory.createEntityManager();
            entityManager.getTransaction().begin();
            
            String tableName = postgresProperties.getTableName();
            int batchSize = postgresProperties.getScheduler().getBatchSize();
            
            logger.debug("Executing cleanup for table '{}' with batch size {}", tableName, batchSize);
            
            // Call the PostgreSQL cleanup function
            String sql = "SELECT cleanup_expired_jdempotent_records(?)";
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, batchSize);
            
            Object result = query.getSingleResult();
            int deletedCount = ((Number) result).intValue();
            
            entityManager.getTransaction().commit();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (deletedCount > 0) {
                logger.info("Successfully deleted {} expired idempotent records from table '{}' in {}ms", 
                           deletedCount, tableName, executionTime);
            } else {
                logger.info("No expired records found to delete from table '{}' (execution time: {}ms)", 
                            tableName, executionTime);
            }
            
            return deletedCount;
            
        } catch (Exception e) {
            if (entityManager != null && entityManager.getTransaction().isActive()) {
                try {
                    entityManager.getTransaction().rollback();
                    logger.debug("Transaction rolled back due to error during cleanup");
                } catch (Exception rollbackException) {
                    logger.error("Failed to rollback transaction after cleanup error", rollbackException);
                }
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("Failed to cleanup expired idempotent records after {}ms. " +
                        "Table: '{}', Batch size: {}, Error: {}", 
                        executionTime, 
                        postgresProperties.getTableName(), 
                        postgresProperties.getScheduler().getBatchSize(), 
                        e.getMessage(), e);
            
            return -1;
            
        } finally {
            if (entityManager != null) {
                try {
                    entityManager.close();
                } catch (Exception closeException) {
                    logger.warn("Failed to close EntityManager after cleanup operation", closeException);
                }
            }
        }
    }


    /**
     * Gets the current configuration summary for monitoring and debugging purposes.
     * 
     * @return a string representation of the current configuration
     */
    public String getConfigurationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("JdempotentPostgresCleanupService Configuration:\n");
        summary.append("  Enabled: ").append(postgresProperties.getScheduler().isEnabled()).append("\n");
        summary.append("  Table Name: ").append(postgresProperties.getTableName()).append("\n");
        summary.append("  Batch Size: ").append(postgresProperties.getScheduler().getBatchSize()).append("\n");
        summary.append("  Scheduling Type: ").append(postgresProperties.getScheduler().getSchedulingType()).append("\n");
        
        switch (postgresProperties.getScheduler().getSchedulingType()) {
            case FIXED_RATE:
                summary.append("  Fixed Rate: ").append(postgresProperties.getScheduler().getFixedRate()).append("ms\n");
                summary.append("  Initial Delay: ").append(postgresProperties.getScheduler().getInitialDelay()).append("ms\n");
                break;
            case CRON:
                summary.append("  Cron Expression: ").append(postgresProperties.getScheduler().getCron()).append("\n");
                summary.append("  Time Zone: ").append(postgresProperties.getScheduler().getZone()).append("\n");
                break;
            case NONE:
                summary.append("  No scheduling configured\n");
                break;
        }
        
        return summary.toString();
    }
}
