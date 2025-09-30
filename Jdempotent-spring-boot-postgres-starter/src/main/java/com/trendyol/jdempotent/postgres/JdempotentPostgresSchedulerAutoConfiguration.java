package com.trendyol.jdempotent.postgres;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityManagerFactory;

/**
 * Auto-configuration class for Jdempotent PostgreSQL scheduled cleanup functionality.
 * 
 * <p>This configuration class automatically sets up the scheduled cleanup service when
 * the appropriate conditions are met. It provides a separate configuration from the main
 * idempotent functionality to allow fine-grained control over scheduling features.</p>
 * 
 * <h3>Activation Conditions:</h3>
 * <ul>
 *   <li>PostgreSQL and JPA classes are available on the classpath</li>
 *   <li>An EntityManagerFactory bean is available</li>
 *   <li>Jdempotent PostgreSQL properties are configured</li>
 *   <li>Scheduler is enabled via {@code jdempotent.postgres.scheduler.enabled=true}</li>
 * </ul>
 * 
 * <h3>Beans Provided:</h3>
 * <ul>
 *   <li>{@link JdempotentPostgresCleanupService} - The cleanup service with scheduled methods</li>
 *   <li>{@link ThreadPoolTaskScheduler} - Custom task scheduler for cleanup operations (optional)</li>
 * </ul>
 * 
 * <h3>Scheduling Configuration:</h3>
 * <p>The auto-configuration enables Spring's {@code @EnableScheduling} and provides a custom
 * task scheduler optimized for cleanup operations. The scheduler can be customized through
 * configuration properties.</p>
 * 
 * <h3>Thread Pool Configuration:</h3>
 * <p>A dedicated thread pool is created for cleanup operations to avoid interfering with
 * the application's main thread pool. The thread pool is configured with:</p>
 * <ul>
 *   <li>Pool size: 1 (single-threaded for cleanup operations)</li>
 *   <li>Thread name prefix: "jdempotent-cleanup-"</li>
 *   <li>Daemon threads: true (won't prevent JVM shutdown)</li>
 * </ul>
 * 
 * <h3>Example Configuration:</h3>
 * <pre>
 * # application.properties
 * jdempotent.postgres.scheduler.enabled=true
 * jdempotent.postgres.scheduler.batchSize=1000
 * jdempotent.postgres.scheduler.fixedDelay=300000
 * jdempotent.postgres.scheduler.initialDelay=60000
 * </pre>
 * 
 * @author Wallyson Soares
 * @since 2.0.0
 * @see JdempotentPostgresProperties
 * @see JdempotentPostgresCleanupService
 */
@EnableScheduling
@AutoConfiguration
@ConditionalOnBean(EntityManagerFactory.class)
@EnableConfigurationProperties({JdempotentPostgresProperties.class})
@ConditionalOnClass({EntityManagerFactory.class, org.postgresql.Driver.class, org.springframework.scheduling.annotation.Scheduled.class})
@ConditionalOnProperty(
    prefix = "jdempotent.postgres.scheduler", 
    name = "enabled", 
    havingValue = "true"
)
public class JdempotentPostgresSchedulerAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JdempotentPostgresSchedulerAutoConfiguration.class);

    private final JdempotentPostgresProperties postgresProperties;
    private final ApplicationContext applicationContext;

    /**
     * Constructs the auto-configuration with required dependencies.
     * 
     * @param postgresProperties the PostgreSQL configuration properties (including scheduler properties)
     * @param applicationContext the Spring application context
     */
    public JdempotentPostgresSchedulerAutoConfiguration(
            JdempotentPostgresProperties postgresProperties,
            ApplicationContext applicationContext) {
        this.postgresProperties = postgresProperties;
        this.applicationContext = applicationContext;
        
        logger.info("Jdempotent PostgreSQL scheduler auto-configuration activated. " +
                   "Scheduling type: {}, Batch size: {}", 
                   postgresProperties.getScheduler().getSchedulingType(), 
                   postgresProperties.getScheduler().getBatchSize());
    }

    /**
     * Creates the cleanup service bean when conditions are met.
     * 
     * <p>This bean is only created when:</p>
     * <ul>
     *   <li>The scheduler is enabled</li>
     *   <li>PostgreSQL properties are available</li>
     *   <li>An EntityManagerFactory is available</li>
     *   <li>No existing cleanup service bean is present</li>
     * </ul>
     * 
     * @return the configured cleanup service
     */
    @Bean
    @ConditionalOnBean(JdempotentPostgresProperties.class)
    @ConditionalOnMissingBean(JdempotentPostgresCleanupService.class)
    public JdempotentPostgresCleanupService jdempotentPostgresCleanupService() {
        
        EntityManagerFactory entityManagerFactory = resolveEntityManagerFactory(postgresProperties);
        
        JdempotentPostgresCleanupService service = new JdempotentPostgresCleanupService(
            entityManagerFactory, 
            postgresProperties
        );
        
        logger.debug("Jdempotent PostgreSQL cleanup service created successfully");
        logger.debug("Cleanup service configuration:\n{}", service.getConfigurationSummary());
        
        return service;
    }

    /**
     * Creates a fixed rate scheduler bean when scheduling type is FIXED_RATE.
     * This ensures only one scheduling mechanism is active at a time.
     */
    @Bean
    @ConditionalOnProperty(name = "jdempotent.postgres.scheduler.type", havingValue = "FIXED_RATE")
    public JdempotentFixedRateScheduler jdempotentFixedRateScheduler(JdempotentPostgresCleanupService cleanupService) {
        return new JdempotentFixedRateScheduler(cleanupService, postgresProperties);
    }

    /**
     * Creates a cron scheduler bean when scheduling type is CRON.
     * This ensures only one scheduling mechanism is active at a time.
     */
    @Bean
    @ConditionalOnProperty(name = "jdempotent.postgres.scheduler.type", havingValue = "CRON")
    public JdempotentCronScheduler jdempotentCronScheduler(JdempotentPostgresCleanupService cleanupService) {
        return new JdempotentCronScheduler(cleanupService, postgresProperties);
    }

    /**
     * Resolves the EntityManagerFactory to use for cleanup operations.
     * 
     * <p>This method follows the same resolution logic as the main PostgreSQL configuration:
     * if a specific EntityManager bean name is configured, it attempts to resolve that bean;
     * otherwise, it uses the default EntityManagerFactory.</p>
     * 
     * @param postgresProperties the PostgreSQL configuration properties
     * @return the resolved EntityManagerFactory
     * @throws IllegalStateException if the EntityManagerFactory cannot be resolved
     */
    private EntityManagerFactory resolveEntityManagerFactory(JdempotentPostgresProperties postgresProperties) {
        String beanName = postgresProperties.getEntityManagerBeanName();
        
        if (!StringUtils.hasText(beanName)) {
            EntityManagerFactory emf = applicationContext.getBean(EntityManagerFactory.class);
            logger.debug("Using default EntityManagerFactory for cleanup operations");
            return emf;
        }
        
        try {
            EntityManagerFactory emf = applicationContext.getBean(beanName, EntityManagerFactory.class);
            logger.debug("Using EntityManagerFactory '{}' for cleanup operations", beanName);
            return emf;
        } catch (Exception e) {
            logger.error("Could not resolve EntityManagerFactory with name '{}' for cleanup operations", beanName, e);
            throw new IllegalStateException(
                "Failed to resolve EntityManagerFactory '" + beanName + "' for cleanup operations", e);
        }
    }
}
