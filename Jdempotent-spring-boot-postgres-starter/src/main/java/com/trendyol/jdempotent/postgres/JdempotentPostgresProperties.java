package com.trendyol.jdempotent.postgres;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Jdempotent PostgreSQL integration.
 * 
 * <p>This class provides configuration options for customizing the behavior of the PostgreSQL
 * idempotent repository implementation and scheduled cleanup functionality. All properties are 
 * optional and have sensible defaults.</p>
 * 
 * <h3>Configuration Properties:</h3>
 * <ul>
 *   <li><strong>jdempotent.postgres.tableName</strong> - Database table name for storing idempotent data</li>
 *   <li><strong>jdempotent.postgres.entityManagerBeanName</strong> - Specific EntityManager bean name for multi-database scenarios</li>
 *   <li><strong>jdempotent.cache.persistReqRes</strong> - Whether to persist request/response data as JSON</li>
 *   <li><strong>jdempotent.postgres.scheduler.enabled</strong> - Enable/disable the scheduled cleanup task</li>
 *   <li><strong>jdempotent.postgres.scheduler.batchSize</strong> - Number of records to delete in each cleanup batch</li>
 *   <li><strong>jdempotent.postgres.scheduler.type</strong> - Scheduling strategy to use (NONE, FIXED_RATE, CRON)</li>
 *   <li><strong>jdempotent.postgres.scheduler.fixedRate</strong> - Fixed rate for cleanup executions (in milliseconds)</li>
 *   <li><strong>jdempotent.postgres.scheduler.initialDelay</strong> - Initial delay before first cleanup execution (in milliseconds)</li>
 *   <li><strong>jdempotent.postgres.scheduler.cron</strong> - Cron expression for cleanup scheduling</li>
 *   <li><strong>jdempotent.postgres.scheduler.zone</strong> - Time zone for cron expression</li>
 * </ul>
 * 
 * <h3>Example Configuration:</h3>
 * <pre>
 * # application.properties
 * jdempotent.postgres.tableName=my_idempotent_table
 * jdempotent.postgres.entityManagerBeanName=myCustomEntityManager
 * jdempotent.cache.persistReqRes=true
 * 
 * # Scheduler configuration
 * jdempotent.postgres.scheduler.enabled=true
 * jdempotent.postgres.scheduler.batchSize=1000
 * jdempotent.postgres.scheduler.type=FIXED_RATE
 * jdempotent.postgres.scheduler.fixedRate=3600000
 * jdempotent.postgres.scheduler.initialDelay=60000
 * </pre>
 * 
 * @author Wallyson Soares
 * @since 2.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "jdempotent.postgres")
@ConditionalOnProperty(
        prefix="jdempotent", name = "enable",
        havingValue = "true",
        matchIfMissing = true)
public class JdempotentPostgresProperties {

    /**
     * The name of the PostgreSQL table used to store idempotent request/response data.
     * 
     * <p>This table will be created using the provided SQL script and should contain columns:
     * idempotency_key, request_data, response_data, created_at, and expires_at.</p>
     * 
     * <p><strong>Default:</strong> "jdempotent"</p>
     * <p><strong>Property:</strong> jdempotent.postgres.tableName</p>
     * 
     * @see #getTableName()
     * @see #setTableName(String)
     */
    private String tableName = "jdempotent";

    /**
     * The name of a specific EntityManager bean to use for database operations.
     * 
     * <p>This is useful in multi-database scenarios where you have multiple EntityManager beans
     * and need to specify which one should be used for idempotent operations. If not specified,
     * the default EntityManager bean will be used.</p>
     * 
     * <p><strong>Default:</strong> "" (empty - uses default EntityManager)</p>
     * <p><strong>Property:</strong> jdempotent.postgres.entityManagerBeanName</p>
     * 
     * <h4>Example Usage:</h4>
     * <pre>
     * # For applications with multiple databases
     * jdempotent.postgres.entityManagerBeanName=primaryEntityManager
     * </pre>
     * 
     * @see #getEntityManagerBeanName()
     * @see #setEntityManagerBeanName(String)
     */
    private String entityManagerBeanName = "";

    /**
     * Whether to persist request and response data as byte arrays in the database.
     * 
     * <p>When enabled, the full request and response objects are serialized to byte arrays using
     * Java serialization and stored in the request_data and response_data BYTEA columns. This 
     * allows for complete audit trails and debugging capabilities but increases storage requirements.</p>
     * 
     * <p>When disabled, only the idempotency key and expiration information are stored,
     * reducing storage overhead but limiting debugging capabilities.</p>
     * 
     * <p><strong>Default:</strong> true</p>
     * <p><strong>Property:</strong> jdempotent.cache.persistReqRes</p>
     * 
     * <h4>Storage Impact:</h4>
     * <ul>
     *   <li><strong>true:</strong> Full request/response data stored as byte arrays (higher storage, better debugging)</li>
     *   <li><strong>false:</strong> Only keys and metadata stored (lower storage, limited debugging)</li>
     * </ul>
     * 
     * <h4>Serialization:</h4>
     * <p>Data is stored using Java's built-in serialization mechanism, which is more efficient than
     * JSON and avoids character encoding issues. Objects must be Serializable to be persisted.</p>
     * 
     * @see #getPersistReqRes()
     * @see #setPersistReqRes(Boolean)
     */
    @Value("${jdempotent.cache.persistReqRes:true}")
    private Boolean persistReqRes;

    /**
     * Scheduler configuration for automated cleanup operations.
     */
    private Scheduler scheduler = new Scheduler();

    /**
     * Gets the configured table name for storing idempotent data.
     * 
     * @return the table name, defaults to "jdempotent" if not configured
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Sets the table name for storing idempotent data.
     * 
     * @param tableName the table name to use
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Gets the configured EntityManager bean name.
     * 
     * @return the EntityManager bean name, or empty string if default should be used
     */
    public String getEntityManagerBeanName() {
        return entityManagerBeanName;
    }

    /**
     * Sets the EntityManager bean name to use for database operations.
     * 
     * @param entityManagerBeanName the bean name of the EntityManager to use,
     *                              or empty string to use the default EntityManager
     */
    public void setEntityManagerBeanName(String entityManagerBeanName) {
        this.entityManagerBeanName = entityManagerBeanName;
    }

    /**
     * Gets whether request/response data should be persisted as byte arrays.
     * 
     * @return true if request/response data should be stored, false otherwise
     */
    public Boolean getPersistReqRes() {
        return persistReqRes;
    }

    /**
     * Sets whether request/response data should be persisted as byte arrays.
     * 
     * @param persistReqRes true to store request/response data as byte arrays,
     *                      false to store only keys and metadata
     */
    public void setPersistReqRes(Boolean persistReqRes) {
        this.persistReqRes = persistReqRes;
    }

    /**
     * Gets the scheduler configuration.
     * 
     * @return the scheduler configuration
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Sets the scheduler configuration.
     * 
     * @param scheduler the scheduler configuration
     */
    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Nested configuration class for scheduler properties.
     */
    public static class Scheduler {

        /**
         * Whether the scheduled cleanup task is enabled.
         * 
         * <p>When disabled, no automatic cleanup of expired records will occur.
         * You can still manually invoke cleanup operations if needed.</p>
         * 
         * <p><strong>Default:</strong> false (disabled by default for safety)</p>
         * <p><strong>Property:</strong> jdempotent.postgres.scheduler.enabled</p>
         */
        private boolean enabled = false;

        /**
         * The number of expired records to delete in each cleanup batch.
         * 
         * <p>This controls how many records are processed in a single cleanup operation.
         * Larger batch sizes can be more efficient but may impact database performance.
         * Smaller batch sizes reduce lock contention and allow for better concurrent access.</p>
         * 
         * <p>The PostgreSQL cleanup function uses SELECT FOR UPDATE SKIP LOCKED to ensure
         * concurrent cleanup operations can work safely together.</p>
         * 
         * <p><strong>Default:</strong> 100</p>
         * <p><strong>Property:</strong> jdempotent.postgres.scheduler.batchSize</p>
         * <p><strong>Range:</strong> 1 to 10,000 (recommended: 100-5000)</p>
         */
        private int batchSize = 100;

        /**
         * The scheduling type to use for cleanup operations.
         * 
         * <p>This property allows clients to explicitly specify which scheduling mechanism
         * to use, rather than inferring it from the presence of other properties.</p>
         * 
         * <p><strong>Default:</strong> NONE</p>
         * <p><strong>Property:</strong> jdempotent.postgres.scheduler.type</p>
         */
        private SchedulingType type = SchedulingType.NONE;

        /**
         * Fixed rate in milliseconds for cleanup executions.
         * 
         * <p>This schedules cleanup operations at regular intervals, regardless of how long
         * each cleanup takes. If a cleanup operation takes longer than the fixed rate,
         * the next execution will start immediately after the current one completes.</p>
         * 
         * <p><strong>Default:</strong> null (not used unless specified)</p>
         * <p><strong>Property:</strong> jdempotent.postgres.scheduler.fixedRate</p>
         * <p><strong>Example:</strong> 3600000 (1 hour)</p>
         */
        private Long fixedRate;

        /**
         * Initial delay in milliseconds before the first cleanup execution.
         * 
         * <p>This allows the application to fully start up before beginning cleanup operations.
         * Only applicable when using fixedRate scheduling.</p>
         * 
         * <p><strong>Default:</strong> 60000 (1 minute)</p>
         * <p><strong>Property:</strong> jdempotent.postgres.scheduler.initialDelay</p>
         */
        private long initialDelay = 60000;

        /**
         * Cron expression for scheduling cleanup operations.
         * 
         * <p>Provides the most flexible scheduling options using standard cron syntax.
         * When specified, this takes precedence over fixedDelay and fixedRate settings.</p>
         * 
         * <p><strong>Default:</strong> null (not used unless specified)</p>
         * <p><strong>Property:</strong> jdempotent.postgres.scheduler.cron</p>
         * 
         * <p>Uses standard cron syntax with six fields: second minute hour day month dayOfWeek</p>
         * <p>Examples: "0 0 2 * * ?" for daily at 2AM, "0 0 *\/6 /* /* ?" for every 6 hours</p>
         */
        private String cron;

        /**
         * Time zone for the cron expression.
         * 
         * <p>Specifies the time zone in which the cron expression should be interpreted.
         * Only applicable when using cron scheduling.</p>
         * 
         * <p><strong>Default:</strong> "UTC"</p>
         * <p><strong>Property:</strong> jdempotent.postgres.scheduler.zone</p>
         * <p><strong>Examples:</strong> "UTC", "America/New_York", "Europe/London", "Asia/Tokyo"</p>
         */
        private String zone = "UTC";

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            if (batchSize < 1 || batchSize > 10000) {
                throw new IllegalArgumentException("Batch size must be between 1 and 10,000");
            }
            this.batchSize = batchSize;
        }

        public SchedulingType getType() {
            return type;
        }

        public void setType(SchedulingType type) {
            this.type = type;
        }

        public Long getFixedRate() {
            return fixedRate;
        }

        public void setFixedRate(Long fixedRate) {
            this.fixedRate = fixedRate;
        }

        public long getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(long initialDelay) {
            this.initialDelay = initialDelay;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }

        /**
         * Determines the scheduling type based on configured properties.
         * 
         * <p>If the type is explicitly set, it takes precedence. Otherwise,
         * it falls back to the legacy behavior of inferring from other properties.</p>
         *
         * @return the scheduling type to use
         */
        public SchedulingType getSchedulingType() {
            if (type != SchedulingType.NONE) {
                return type;
            }

            if (cron != null && !cron.trim().isEmpty()) {
                return SchedulingType.CRON;
            }
            if (fixedRate != null) {
                return SchedulingType.FIXED_RATE;
            }
            return SchedulingType.NONE;
        }
    }

    /**
     * Enumeration of supported scheduling types.
     */
    public enum SchedulingType {
        NONE,
        FIXED_RATE,
        CRON
    }
}
