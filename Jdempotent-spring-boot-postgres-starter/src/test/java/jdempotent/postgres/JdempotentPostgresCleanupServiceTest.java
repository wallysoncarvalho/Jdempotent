package jdempotent.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.trendyol.jdempotent.postgres.JdempotentPostgresCleanupService;
import com.trendyol.jdempotent.postgres.JdempotentPostgresProperties;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;

/**
 * Unit tests for JdempotentPostgresCleanupService.
 * 
 * Tests cleanup operations, error handling, and scheduling logic
 * using mocked database components.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JdempotentPostgresCleanupServiceTest {

    @Mock
    private EntityManagerFactory entityManagerFactory;
    
    @Mock
    private EntityManager entityManager;
    
    @Mock
    private EntityTransaction transaction;
    
    @Mock
    private Query query;
    
    private JdempotentPostgresProperties postgresProperties;
    private JdempotentPostgresCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        postgresProperties = new JdempotentPostgresProperties();
        postgresProperties.setTableName("jdempotent");
        
        // Configure scheduler properties using nested structure
        postgresProperties.getScheduler().setEnabled(true);
        postgresProperties.getScheduler().setBatchSize(1000);
        
        cleanupService = new JdempotentPostgresCleanupService(
            entityManagerFactory, 
            postgresProperties
        );
        
        // Setup common mock behavior
        when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        when(entityManager.getTransaction()).thenReturn(transaction);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    @Test
    void test_successful_cleanup() {
        // Arrange
        int expectedDeletedCount = 150;
        when(query.getSingleResult()).thenReturn(expectedDeletedCount);
        
        // Act
        int actualDeletedCount = cleanupService.performCleanup();
        
        // Assert
        assertEquals(expectedDeletedCount, actualDeletedCount);
        
        verify(entityManagerFactory).createEntityManager();
        verify(transaction).begin();
        verify(entityManager).createNativeQuery("SELECT cleanup_expired_jdempotent_records(?)");
        verify(query).setParameter(1, 1000);
        verify(query).getSingleResult();
        verify(transaction).commit();
        verify(entityManager).close();
    }

    @Test
    void test_cleanup_with_no_expired_records() {
        // Arrange
        when(query.getSingleResult()).thenReturn(0);
        
        // Act
        int deletedCount = cleanupService.performCleanup();
        
        // Assert
        assertEquals(0, deletedCount);
        
        verify(transaction).commit();
        verify(entityManager).close();
    }

    @Test
    void test_cleanup_with_custom_batch_size() {
        // Arrange
        postgresProperties.getScheduler().setBatchSize(500);
        when(query.getSingleResult()).thenReturn(250);
        
        // Act
        int deletedCount = cleanupService.performCleanup();
        
        // Assert
        assertEquals(250, deletedCount);
        verify(query).setParameter(1, 500);
    }

    @Test
    void test_cleanup_with_custom_table_name() {
        // Arrange
        postgresProperties.setTableName("custom_idempotent_table");
        when(query.getSingleResult()).thenReturn(75);
        
        // Act
        int deletedCount = cleanupService.performCleanup();
        
        // Assert
        assertEquals(75, deletedCount);
        // The table name is used in the PostgreSQL function, not in the Java query
        verify(entityManager).createNativeQuery("SELECT cleanup_expired_jdempotent_records(?)");
    }

    @Test
    void test_cleanup_failure_with_exception() {
        // Arrange
        RuntimeException testException = new RuntimeException("Database connection failed");
        when(query.getSingleResult()).thenThrow(testException);
        when(transaction.isActive()).thenReturn(true);
        
        // Act
        int deletedCount = cleanupService.performCleanup();
        
        // Assert
        assertEquals(-1, deletedCount);
        
        verify(transaction).begin();
        verify(transaction).rollback();
        verify(entityManager).close();
    }

    @Test
    void test_cleanup_failure_with_rollback_exception() {
        // Arrange
        RuntimeException queryException = new RuntimeException("Query failed");
        RuntimeException rollbackException = new RuntimeException("Rollback failed");
        
        when(query.getSingleResult()).thenThrow(queryException);
        when(transaction.isActive()).thenReturn(true);
        doThrow(rollbackException).when(transaction).rollback();
        
        // Act
        int deletedCount = cleanupService.performCleanup();
        
        // Assert
        assertEquals(-1, deletedCount);
        
        verify(transaction).rollback();
        verify(entityManager).close();
    }

    @Test
    void test_cleanup_failure_with_entity_manager_close_exception() {
        // Arrange
        RuntimeException queryException = new RuntimeException("Query failed");
        RuntimeException closeException = new RuntimeException("Close failed");
        
        when(query.getSingleResult()).thenThrow(queryException);
        when(transaction.isActive()).thenReturn(true);
        doThrow(closeException).when(entityManager).close();
        
        // Act
        int deletedCount = cleanupService.performCleanup();
        
        // Assert
        assertEquals(-1, deletedCount);
        
        verify(transaction).rollback();
        verify(entityManager).close();
    }



    @Test
    void test_execute_scheduled_cleanup() {
        // Arrange
        when(query.getSingleResult()).thenReturn(30);
        
        // Act
        cleanupService.executeScheduledCleanup();
        
        // Assert
        verify(entityManagerFactory).createEntityManager();
        verify(query).getSingleResult();
    }

    @Test
    void test_get_configuration_summary() {
        // Arrange
        postgresProperties.getScheduler().setEnabled(true);
        postgresProperties.getScheduler().setBatchSize(2000);
        postgresProperties.getScheduler().setFixedRate(300000L);
        postgresProperties.getScheduler().setInitialDelay(120000L);
        postgresProperties.setTableName("custom_table");
        
        // Act
        String summary = cleanupService.getConfigurationSummary();
        
        // Assert
        assertNotNull(summary);
        assertTrue(summary.contains("Enabled: true"));
        assertTrue(summary.contains("Table Name: custom_table"));
        assertTrue(summary.contains("Batch Size: 2000"));
        assertTrue(summary.contains("Scheduling Type: FIXED_RATE"));
        assertTrue(summary.contains("Fixed Rate: 300000ms"));
        assertTrue(summary.contains("Initial Delay: 120000ms"));
    }

    @Test
    void test_get_configuration_summary_with_cron() {
        // Arrange
        postgresProperties.getScheduler().setEnabled(true);
        postgresProperties.getScheduler().setCron("0 0 2 * * ?");
        postgresProperties.getScheduler().setZone("America/New_York");
        
        // Act
        String summary = cleanupService.getConfigurationSummary();
        
        // Assert
        assertNotNull(summary);
        assertTrue(summary.contains("Scheduling Type: CRON"));
        assertTrue(summary.contains("Cron Expression: 0 0 2 * * ?"));
        assertTrue(summary.contains("Time Zone: America/New_York"));
    }

    @Test
    void test_get_configuration_summary_with_no_scheduling() {
        // Arrange
        postgresProperties.getScheduler().setEnabled(false);
        
        // Act
        String summary = cleanupService.getConfigurationSummary();
        
        // Assert
        assertNotNull(summary);
        assertTrue(summary.contains("Enabled: false"));
        assertTrue(summary.contains("Scheduling Type: NONE"));
        assertTrue(summary.contains("No scheduling configured"));
    }

    @Test
    void test_cleanup_with_large_delete_count() {
        // Arrange
        int largeDeleteCount = 50000;
        when(query.getSingleResult()).thenReturn(largeDeleteCount);
        
        // Act
        int deletedCount = cleanupService.performCleanup();
        
        // Assert
        assertEquals(largeDeleteCount, deletedCount);
        verify(transaction).commit();
    }

    @Test
    void test_cleanup_with_different_number_types() {
        // Test with different Number implementations that might be returned by the database
        
        // Test with Integer
        when(query.getSingleResult()).thenReturn(Integer.valueOf(100));
        assertEquals(100, cleanupService.performCleanup());
        
        // Reset mocks for next test
        reset(entityManagerFactory, entityManager, transaction, query);
        when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        when(entityManager.getTransaction()).thenReturn(transaction);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        
        // Test with Long
        when(query.getSingleResult()).thenReturn(Long.valueOf(200L));
        assertEquals(200, cleanupService.performCleanup());
        
        // Reset mocks for next test
        reset(entityManagerFactory, entityManager, transaction, query);
        when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        when(entityManager.getTransaction()).thenReturn(transaction);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        
        // Test with BigInteger
        when(query.getSingleResult()).thenReturn(java.math.BigInteger.valueOf(300));
        assertEquals(300, cleanupService.performCleanup());
    }
}
