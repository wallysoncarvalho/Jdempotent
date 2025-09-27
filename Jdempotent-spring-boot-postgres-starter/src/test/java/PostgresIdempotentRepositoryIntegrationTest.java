package com.trendyol.jdempotent.postgres;

import com.trendyol.jdempotent.core.datasource.RequestAlreadyExistsException;
import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;
import com.trendyol.jdempotent.core.model.IdempotentResponseWrapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgresIdempotentRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("jdempotent_test")
            .withUsername("test")
            .withPassword("test");

    private static EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private PostgresIdempotentRepository repository;
    private JdempotentPostgresProperties properties;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        postgres.start();
        
        // Create the table using the SQL script
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            
            // Create the jdempotent table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS jdempotent (
                    idempotency_key VARCHAR(255) PRIMARY KEY,
                    request_data BYTEA,
                    response_data BYTEA,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP
                );
                """);
            
            // Create index for efficient cleanup
            statement.execute("CREATE INDEX IF NOT EXISTS idx_jdempotent_expires_at ON jdempotent(expires_at);");
            
            // Create cleanup function
            statement.execute("""
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
                """);
        }

        // Set up JPA EntityManagerFactory
        Map<String, String> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        props.put("jakarta.persistence.jdbc.url", postgres.getJdbcUrl());
        props.put("jakarta.persistence.jdbc.user", postgres.getUsername());
        props.put("jakarta.persistence.jdbc.password", postgres.getPassword());
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.show_sql", "false");

        entityManagerFactory = Persistence.createEntityManagerFactory("test-unit", props);
    }

    @AfterAll
    static void tearDown() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
        postgres.stop();
    }

    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        
        properties = new JdempotentPostgresProperties();
        properties.setTableName("jdempotent");
        properties.setPersistReqRes(true);
        
        repository = new PostgresIdempotentRepository(entityManager, properties);
        
        // Clean up any existing data
        cleanupTable();
    }

    @AfterEach
    void tearDownEach() {
        if (entityManager != null) {
            cleanupTable();
            entityManager.close();
        }
    }

    private void cleanupTable() {
        try {
            entityManager.getTransaction().begin();
            entityManager.createNativeQuery("DELETE FROM jdempotent").executeUpdate();
            entityManager.getTransaction().commit();
        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
        }
    }

    @Test
    void testContains_WhenKeyExists_ReturnsTrue() {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key-exists");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("test-request"));
        
        // When
        assertDoesNotThrow(() -> repository.store(key, request));
        boolean result = repository.contains(key);
        
        // Then
        assertTrue(result);
    }

    @Test
    void testContains_WhenKeyDoesNotExist_ReturnsFalse() {
        // Given
        IdempotencyKey key = new IdempotencyKey("non-existent-key");
        
        // When
        boolean result = repository.contains(key);
        
        // Then
        assertFalse(result);
    }

    @Test
    void testStore_WhenKeyDoesNotExist_StoresSuccessfully() throws RequestAlreadyExistsException {
        // Given
        IdempotencyKey key = new IdempotencyKey("new-key");
        TestData testData = new TestData("test-request-data");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(testData);
        
        // When
        assertDoesNotThrow(() -> repository.store(key, request));
        
        // Then
        assertTrue(repository.contains(key));
        
        IdempotentRequestResponseWrapper wrapper = repository.getRequestResponseWrapper(key);
        assertNotNull(wrapper);
        assertNotNull(wrapper.getRequest());
        assertEquals(testData.getValue(), ((TestData) wrapper.getRequest().getRequest()).getValue());
    }

    @Test
    void testStore_WhenKeyExists_ThrowsException() throws RequestAlreadyExistsException {
        // Given
        IdempotencyKey key = new IdempotencyKey("duplicate-key");
        IdempotentRequestWrapper request1 = new IdempotentRequestWrapper(new TestData("first-request"));
        IdempotentRequestWrapper request2 = new IdempotentRequestWrapper(new TestData("second-request"));
        
        repository.store(key, request1);
        
        // When & Then
        assertThrows(RequestAlreadyExistsException.class, () -> {
            repository.store(key, request2);
        });
    }

    @Test
    void testStore_WithTTL_ExpiresCorrectly() throws RequestAlreadyExistsException, InterruptedException {
        // Given
        IdempotencyKey key = new IdempotencyKey("ttl-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("ttl-request"));
        
        // When - store with 1 second TTL
        repository.store(key, request, 1L, TimeUnit.SECONDS);
        
        // Then - should exist initially
        assertTrue(repository.contains(key));
        
        // Wait for expiration
        Thread.sleep(1100);
        
        // Should not exist after expiration
        assertFalse(repository.contains(key));
    }

    @Test
    void testGetResponse_WhenResponseExists_ReturnsResponse() throws RequestAlreadyExistsException {
        // Given
        IdempotencyKey key = new IdempotencyKey("response-key");
        TestData requestData = new TestData("request-data");
        TestData responseData = new TestData("response-data");
        
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);
        IdempotentResponseWrapper response = new IdempotentResponseWrapper(responseData);
        
        repository.store(key, request);
        repository.setResponse(key, request, response);
        
        // When
        IdempotentResponseWrapper result = repository.getResponse(key);
        
        // Then
        assertNotNull(result);
        assertEquals(responseData.getValue(), ((TestData) result.getResponse()).getValue());
    }

    @Test
    void testGetResponse_WhenResponseDoesNotExist_ReturnsNull() {
        // Given
        IdempotencyKey key = new IdempotencyKey("no-response-key");
        
        // When
        IdempotentResponseWrapper result = repository.getResponse(key);
        
        // Then
        assertNull(result);
    }

    @Test
    void testGetRequestResponseWrapper_WhenDataExists_ReturnsWrapper() throws RequestAlreadyExistsException {
        // Given
        IdempotencyKey key = new IdempotencyKey("wrapper-key");
        TestData requestData = new TestData("wrapper-request");
        TestData responseData = new TestData("wrapper-response");
        
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);
        IdempotentResponseWrapper response = new IdempotentResponseWrapper(responseData);
        
        repository.store(key, request);
        repository.setResponse(key, request, response);
        
        // When
        IdempotentRequestResponseWrapper result = repository.getRequestResponseWrapper(key);
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getRequest());
        assertNotNull(result.getResponse());
        assertEquals(requestData.getValue(), ((TestData) result.getRequest().getRequest()).getValue());
        assertEquals(responseData.getValue(), ((TestData) result.getResponse().getResponse()).getValue());
    }

    @Test
    void testRemove_RemovesKeySuccessfully() throws RequestAlreadyExistsException {
        // Given
        IdempotencyKey key = new IdempotencyKey("remove-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("remove-request"));
        
        repository.store(key, request);
        assertTrue(repository.contains(key));
        
        // When
        repository.remove(key);
        
        // Then
        assertFalse(repository.contains(key));
    }

    @Test
    void testSetResponse_WhenKeyExists_UpdatesResponse() throws RequestAlreadyExistsException {
        // Given
        IdempotencyKey key = new IdempotencyKey("update-response-key");
        TestData requestData = new TestData("update-request");
        TestData responseData = new TestData("update-response");
        
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);
        IdempotentResponseWrapper response = new IdempotentResponseWrapper(responseData);
        
        repository.store(key, request);
        
        // When
        repository.setResponse(key, request, response);
        
        // Then
        IdempotentResponseWrapper result = repository.getResponse(key);
        assertNotNull(result);
        assertEquals(responseData.getValue(), ((TestData) result.getResponse()).getValue());
    }

    @Test
    void testSetResponse_WithTTL_UpdatesExpirationTime() throws RequestAlreadyExistsException, InterruptedException {
        // Given
        IdempotencyKey key = new IdempotencyKey("ttl-response-key");
        TestData requestData = new TestData("ttl-request");
        TestData responseData = new TestData("ttl-response");
        
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);
        IdempotentResponseWrapper response = new IdempotentResponseWrapper(responseData);
        
        repository.store(key, request);
        
        // When - set response with 1 second TTL
        repository.setResponse(key, request, response, 1L, TimeUnit.SECONDS);
        
        // Then - should exist initially
        assertTrue(repository.contains(key));
        
        // Wait for expiration
        Thread.sleep(1100);
        
        // Should not exist after expiration
        assertFalse(repository.contains(key));
    }

    @Test
    void testPersistReqRes_WhenFalse_DoesNotStoreData() throws RequestAlreadyExistsException {
        // Given
        properties.setPersistReqRes(false);
        repository = new PostgresIdempotentRepository(entityManager, properties);
        
        IdempotencyKey key = new IdempotencyKey("no-persist-key");
        TestData requestData = new TestData("no-persist-request");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);
        
        // When
        repository.store(key, request);
        
        // Then - key should exist but no data should be stored
        assertTrue(repository.contains(key));
        
        IdempotentRequestResponseWrapper wrapper = repository.getRequestResponseWrapper(key);
        assertNotNull(wrapper);
        assertNull(wrapper.getRequest()); // No request data stored
        assertNull(wrapper.getResponse()); // No response data stored
    }

    @Test
    void testCleanupExpiredRecords_RemovesExpiredEntries() throws Exception {
        // Given - store entries with different expiration times
        IdempotencyKey expiredKey = new IdempotencyKey("expired-key");
        IdempotencyKey validKey = new IdempotencyKey("valid-key");
        
        IdempotentRequestWrapper request1 = new IdempotentRequestWrapper(new TestData("expired-request"));
        IdempotentRequestWrapper request2 = new IdempotentRequestWrapper(new TestData("valid-request"));
        
        // Store with very short TTL (already expired)
        repository.store(expiredKey, request1, 1L, TimeUnit.MILLISECONDS);
        Thread.sleep(10); // Ensure expiration
        
        // Store with long TTL
        repository.store(validKey, request2, 1L, TimeUnit.HOURS);
        
        // When - run cleanup function
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            
            var result = statement.executeQuery("SELECT cleanup_expired_jdempotent_records()");
            result.next();
            int deletedCount = result.getInt(1);
            
            // Then
            assertEquals(1, deletedCount); // One expired record should be deleted
            assertFalse(repository.contains(expiredKey)); // Expired key should be gone
            assertTrue(repository.contains(validKey)); // Valid key should remain
        }
    }

    /**
     * Test data class that implements Serializable for byte array serialization
     */
    static class TestData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String value;

        public TestData(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestData testData = (TestData) obj;
            return value != null ? value.equals(testData.value) : testData.value == null;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "TestData{value='" + value + "'}";
        }
    }
}
