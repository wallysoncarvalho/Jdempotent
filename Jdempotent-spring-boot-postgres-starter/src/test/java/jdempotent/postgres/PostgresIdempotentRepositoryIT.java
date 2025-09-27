package jdempotent.postgres;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.trendyol.jdempotent.core.datasource.RequestAlreadyExistsException;
import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;
import com.trendyol.jdempotent.core.model.IdempotentResponseWrapper;
import com.trendyol.jdempotent.postgres.JdempotentPostgresProperties;
import com.trendyol.jdempotent.postgres.PostgresIdempotentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

@Testcontainers
class PostgresIdempotentRepositoryIT {

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
                    cache_prefix VARCHAR(255),
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
    void testStore_ConcurrentRequests_OnlyOneSucceeds() throws Exception {
        // Given
        IdempotencyKey key = new IdempotencyKey("concurrent-key");
        TestData requestData1 = new TestData("concurrent-request-1");
        TestData requestData2 = new TestData("concurrent-request-2");
        
        IdempotentRequestWrapper request1 = new IdempotentRequestWrapper(requestData1);
        IdempotentRequestWrapper request2 = new IdempotentRequestWrapper(requestData2);
        
        // When - simulate concurrent requests using threads
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch completeLatch = new CountDownLatch(2);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        // Create two competing tasks
        Runnable task1 = () -> {
            try {
                readyLatch.countDown(); // Signal this thread is ready
                startLatch.await(); // Wait for both threads to be ready
                repository.store(key, request1);
                successCount.incrementAndGet();
            } catch (RequestAlreadyExistsException e) {
                exceptionCount.incrementAndGet();
            } catch (Exception e) {
                // Unexpected exception
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                completeLatch.countDown();
            }
        };
        
        Runnable task2 = () -> {
            try {
                readyLatch.countDown(); // Signal this thread is ready
                startLatch.await(); // Wait for both threads to be ready
                repository.store(key, request2);
                successCount.incrementAndGet();
            } catch (RequestAlreadyExistsException e) {
                exceptionCount.incrementAndGet();
            } catch (Exception e) {
                // Unexpected exception
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                completeLatch.countDown();
            }
        };
        
        // Submit both tasks
        executor.submit(task1);
        executor.submit(task2);
        
        // Wait for both threads to be ready
        boolean ready = readyLatch.await(2, TimeUnit.SECONDS);
        assertTrue(ready, "Both threads should be ready within 2 seconds");
        
        // Start both threads simultaneously
        startLatch.countDown();
        
        // Wait for both to complete
        boolean completed = completeLatch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "Both threads should complete within 5 seconds");
        
        executor.shutdown();
        
        // Then - exactly one should succeed, one should get RequestAlreadyExistsException
        System.out.println("Success count: " + successCount.get() + ", Exception count: " + exceptionCount.get());
        assertEquals(2, successCount.get() + exceptionCount.get(), "Total operations should be 2");
        assertEquals(1, successCount.get(), "Exactly one request should succeed");
        assertEquals(1, exceptionCount.get(), "Exactly one request should get RequestAlreadyExistsException");
        
        // Verify the key exists in the database
        assertTrue(repository.contains(key), "The key should exist in the database");
        
        // Verify we can retrieve the stored data
        IdempotentRequestResponseWrapper wrapper = repository.getRequestResponseWrapper(key);
        assertNotNull(wrapper);
        assertNotNull(wrapper.getRequest());
        
        // The stored data should be from one of the requests
        TestData storedData = (TestData) wrapper.getRequest().getRequest();
        assertTrue(
            requestData1.getValue().equals(storedData.getValue()) || 
            requestData2.getValue().equals(storedData.getValue()),
            "Stored data should match one of the concurrent requests"
        );
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

    @Test
    void testStore_WithCachePrefix_StoresCachePrefixCorrectly() throws Exception {
        // Given
        IdempotencyKey key = new IdempotencyKey("cache-prefix-key");
        TestData requestData = new TestData("cache-prefix-request");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);
        String cachePrefix = "user-service";

        // When
        repository.store(key, request, cachePrefix, 3600L, TimeUnit.SECONDS);

        // Then
        assertTrue(repository.contains(key));

        // Verify cache prefix is stored by checking the database directly
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            
            String sql = "SELECT cache_prefix FROM " + properties.getTableName() + 
                        " WHERE idempotency_key = '" + key.getKeyValue() + "'";
            var rs = stmt.executeQuery(sql);
            
            assertTrue(rs.next(), "Record should exist in database");
            assertEquals(cachePrefix, rs.getString("cache_prefix"), "Cache prefix should be stored correctly");
        }
    }

    @Test
    void testStore_WithNullCachePrefix_StoresNullCachePrefix() throws Exception {
        // Given
        IdempotencyKey key = new IdempotencyKey("null-cache-prefix-key");
        TestData requestData = new TestData("null-cache-prefix-request");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);

        // When
        repository.store(key, request, null, 3600L, TimeUnit.SECONDS);

        // Then
        assertTrue(repository.contains(key));

        // Verify cache prefix is null by checking the database directly
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            
            String sql = "SELECT cache_prefix FROM " + properties.getTableName() + 
                        " WHERE idempotency_key = '" + key.getKeyValue() + "'";
            var rs = stmt.executeQuery(sql);
            
            assertTrue(rs.next(), "Record should exist in database");
            assertNull(rs.getString("cache_prefix"), "Cache prefix should be null");
        }
    }

    @Test
    void testStore_WithEmptyCachePrefix_StoresEmptyCachePrefix() throws Exception {
        // Given
        IdempotencyKey key = new IdempotencyKey("empty-cache-prefix-key");
        TestData requestData = new TestData("empty-cache-prefix-request");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);
        String cachePrefix = "";

        // When
        repository.store(key, request, cachePrefix, 3600L, TimeUnit.SECONDS);

        // Then
        assertTrue(repository.contains(key));

        // Verify empty cache prefix is stored by checking the database directly
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            
            String sql = "SELECT cache_prefix FROM " + properties.getTableName() + 
                        " WHERE idempotency_key = '" + key.getKeyValue() + "'";
            var rs = stmt.executeQuery(sql);
            
            assertTrue(rs.next(), "Record should exist in database");
            assertEquals("", rs.getString("cache_prefix"), "Cache prefix should be empty string");
        }
    }
}
