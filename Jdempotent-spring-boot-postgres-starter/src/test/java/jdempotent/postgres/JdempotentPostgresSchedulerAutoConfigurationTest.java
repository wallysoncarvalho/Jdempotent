package jdempotent.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.trendyol.jdempotent.postgres.JdempotentPostgresCleanupService;
import com.trendyol.jdempotent.postgres.JdempotentPostgresProperties;
import com.trendyol.jdempotent.postgres.JdempotentPostgresSchedulerAutoConfiguration;

import jakarta.persistence.EntityManagerFactory;

/**
 * Integration tests for JdempotentPostgresSchedulerAutoConfiguration.
 * 
 * Tests auto-configuration behavior under different conditions and property configurations.
 */
class JdempotentPostgresSchedulerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JdempotentPostgresSchedulerAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void test_auto_configuration_not_activated_when_scheduler_disabled() {
        contextRunner
                .withPropertyValues("jdempotent.postgres.scheduler.enabled=false")
                .run(context -> {
                    assertEquals(0, context.getBeansOfType(JdempotentPostgresSchedulerAutoConfiguration.class).size());
                    assertEquals(0, context.getBeansOfType(JdempotentPostgresCleanupService.class).size());
                    assertTrue(!context.containsBean("jdempotentCleanupTaskScheduler"));
                });
    }

    @Test
    void test_auto_configuration_activated_when_scheduler_enabled() {
        contextRunner
                .withPropertyValues(
                    "jdempotent.postgres.scheduler.enabled=true",
                    "jdempotent.postgres.scheduler.type=FIXED_RATE",
                    "jdempotent.postgres.scheduler.batchSize=500",
                    "jdempotent.postgres.scheduler.fixedRate=300000"
                )
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(JdempotentPostgresSchedulerAutoConfiguration.class).size());
                    assertEquals(1, context.getBeansOfType(JdempotentPostgresProperties.class).size());
                    assertEquals(1, context.getBeansOfType(JdempotentPostgresCleanupService.class).size());
                    
                    JdempotentPostgresProperties properties = context.getBean(JdempotentPostgresProperties.class);
                    assertTrue(properties.getScheduler().isEnabled());
                    assertEquals(500, properties.getScheduler().getBatchSize());
                    assertEquals(300000L, properties.getScheduler().getFixedRate());
                });
    }

    @Test
    void test_scheduler_properties_configuration() {
        contextRunner
                .withPropertyValues(
                    "jdempotent.postgres.scheduler.enabled=true",
                    "jdempotent.postgres.scheduler.type=FIXED_RATE",
                    "jdempotent.postgres.scheduler.batchSize=2000",
                    "jdempotent.postgres.scheduler.fixedRate=3600000",
                    "jdempotent.postgres.scheduler.initialDelay=120000"
                )
                .run(context -> {
                    JdempotentPostgresProperties properties = context.getBean(JdempotentPostgresProperties.class);
                    
                    assertTrue(properties.getScheduler().isEnabled());
                    assertEquals(2000, properties.getScheduler().getBatchSize());
                    assertEquals(3600000L, properties.getScheduler().getFixedRate());
                    assertEquals(120000L, properties.getScheduler().getInitialDelay());
                    assertEquals(JdempotentPostgresProperties.SchedulingType.FIXED_RATE, 
                        properties.getScheduler().getSchedulingType());
                });
    }

    @Test
    void test_cron_scheduling_configuration() {
        contextRunner
                .withPropertyValues(
                    "jdempotent.postgres.scheduler.enabled=true",
                    "jdempotent.postgres.scheduler.cron=0 0 2 * * ?",
                    "jdempotent.postgres.scheduler.zone=America/New_York"
                )
                .run(context -> {
                    JdempotentPostgresProperties properties = context.getBean(JdempotentPostgresProperties.class);
                    
                    assertEquals("0 0 2 * * ?", properties.getScheduler().getCron());
                    assertEquals("America/New_York", properties.getScheduler().getZone());
                    assertEquals(JdempotentPostgresProperties.SchedulingType.CRON, 
                        properties.getScheduler().getSchedulingType());
                });
    }

    @Test
    void test_cleanup_service_creation() {
        contextRunner
                .withPropertyValues(
                    "jdempotent.postgres.scheduler.enabled=true",
                    "jdempotent.postgres.scheduler.batchSize=1500"
                )
                .run(context -> {
                    JdempotentPostgresCleanupService cleanupService = context.getBean(JdempotentPostgresCleanupService.class);
                    
                    assertNotNull(cleanupService);
                    
                    String configSummary = cleanupService.getConfigurationSummary();
                    assertTrue(configSummary.contains("Enabled: true"));
                    assertTrue(configSummary.contains("Batch Size: 1500"));
                });
    }


    @Test
    void test_postgres_properties_integration() {
        contextRunner
                .withPropertyValues(
                    "jdempotent.postgres.scheduler.enabled=true",
                    "jdempotent.postgres.tableName=custom_idempotent_table"
                )
                .run(context -> {
                    JdempotentPostgresProperties postgresProperties = 
                        context.getBean(JdempotentPostgresProperties.class);
                    
                    assertEquals("custom_idempotent_table", postgresProperties.getTableName());
                    
                    JdempotentPostgresCleanupService cleanupService = 
                        context.getBean(JdempotentPostgresCleanupService.class);
                    
                    String configSummary = cleanupService.getConfigurationSummary();
                    assertTrue(configSummary.contains("Table Name: custom_idempotent_table"));
                });
    }

    @Test
    void test_default_property_values() {
        contextRunner
                .withPropertyValues("jdempotent.postgres.scheduler.enabled=true")
                .run(context -> {
                    JdempotentPostgresProperties properties = 
                        context.getBean(JdempotentPostgresProperties.class);
                    
                    assertEquals(100, properties.getScheduler().getBatchSize());
                    assertEquals(60000, properties.getScheduler().getInitialDelay());
                    assertEquals("UTC", properties.getScheduler().getZone());
                    assertNull(properties.getScheduler().getFixedRate());
                    assertNull(properties.getScheduler().getCron());
                    assertEquals(JdempotentPostgresProperties.SchedulingType.NONE, 
                        properties.getScheduler().getSchedulingType());
                });
    }

    @Test
    void test_configuration_validation() {
        contextRunner
                .withPropertyValues(
                    "jdempotent.postgres.scheduler.enabled=true",
                    "jdempotent.postgres.scheduler.batchSize=10000" // Large batch size should trigger warning
                )
                .run(context -> {
                    // Configuration should still be created despite warnings
                    assertEquals(1, context.getBeansOfType(JdempotentPostgresSchedulerAutoConfiguration.class).size());
                    assertEquals(1, context.getBeansOfType(JdempotentPostgresCleanupService.class).size());
                    
                    JdempotentPostgresProperties properties = 
                        context.getBean(JdempotentPostgresProperties.class);
                    assertEquals(10000, properties.getScheduler().getBatchSize());
                });
    }

    @Test
    void test_multiple_scheduling_options_configured() {
        contextRunner
                .withPropertyValues(
                    "jdempotent.postgres.scheduler.enabled=true",
                    "jdempotent.postgres.scheduler.type=CRON",
                    "jdempotent.postgres.scheduler.fixedRate=3600000",
                    "jdempotent.postgres.scheduler.cron=0 0 2 * * ?"
                )
                .run(context -> {
                    JdempotentPostgresProperties properties = 
                        context.getBean(JdempotentPostgresProperties.class);
                    
                    // Cron should have highest priority
                    assertEquals(JdempotentPostgresProperties.SchedulingType.CRON, 
                        properties.getScheduler().getSchedulingType());
                    assertEquals("0 0 2 * * ?", properties.getScheduler().getCron());
                });
    }

    @Test
    void test_no_scheduling_configured() {
        contextRunner
                .withPropertyValues("jdempotent.postgres.scheduler.enabled=true")
                .run(context -> {
                    JdempotentPostgresProperties properties = 
                        context.getBean(JdempotentPostgresProperties.class);
                    
                    assertEquals(JdempotentPostgresProperties.SchedulingType.NONE, 
                        properties.getScheduler().getSchedulingType());
                    
                    // Configuration should still be created but with warning
                    assertEquals(1, context.getBeansOfType(JdempotentPostgresCleanupService.class).size());
                });
    }

    /**
     * Test configuration that provides required beans for the auto-configuration.
     */
    @Configuration
    @EnableConfigurationProperties(JdempotentPostgresProperties.class)
    static class TestConfiguration {

        @Bean
        public EntityManagerFactory entityManagerFactory() {
            return mock(EntityManagerFactory.class);
        }
    }
}
