package jdempotent.postgres;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
            .withPassword("test")
            .withInitScript("jdempotent-table.sql");

    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private JdempotentPostgresProperties properties;
    private PostgresIdempotentRepository repository;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        postgres.start();

        Map<String, String> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        props.put("jakarta.persistence.jdbc.url", postgres.getJdbcUrl());
        props.put("jakarta.persistence.jdbc.user", postgres.getUsername());
        props.put("jakarta.persistence.jdbc.password", postgres.getPassword());
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.hbm2ddl.auto", "validate");

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
        repository = new PostgresIdempotentRepository(entityManagerFactory, properties);
        cleanupTable(entityManager);
    }

    @AfterEach
    void tearDownEach() {
        if (entityManager != null) {
            try {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
            } finally {
                entityManager.close();
            }
        }
    }

    private void cleanupTable(EntityManager em) {
        try {
            em.getTransaction().begin();
            em.createNativeQuery("DELETE FROM jdempotent").executeUpdate();
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
        }
    }

    @Test
    void testStore_WhenKeyDoesNotExist_StoresSuccessfully() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("test-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("test-request"));

        repository.store(key, request);

        assertTrue(repository.contains(key));
    }

    @Test
    void testStore_WhenKeyExists_ThrowsException() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("duplicate-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("request-1")));

        assertThrows(RequestAlreadyExistsException.class,
                () -> repository.store(key, new IdempotentRequestWrapper(new TestData("request-2"))));
    }

    @Test
    void testContains_WhenKeyExists_ReturnsTrue() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("existing-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("test-request")));

        assertTrue(repository.contains(key));
    }

    @Test
    void testContains_WhenKeyDoesNotExist_ReturnsFalse() {
        assertFalse(repository.contains(new IdempotencyKey("non-existing-key")));
    }

    @Test
    void testGetResponse_WhenResponseExists_ReturnsResponse() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("response-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("test-request"));
        IdempotentResponseWrapper response = new IdempotentResponseWrapper(new TestData("test-response"));

        repository.store(key, request);
        repository.setResponse(key, request, response);

        IdempotentResponseWrapper result = repository.getResponse(key);

        assertNotNull(result);
        assertEquals("test-response", ((TestData) result.getResponse()).getValue());
    }

    @Test
    void testGetResponse_WhenResponseDoesNotExist_ReturnsNull() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("no-response-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("test-request")));

        assertNull(repository.getResponse(key));
    }

    @Test
    void testGetRequestResponseWrapper_WhenDataExists_ReturnsWrapper() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("wrapper-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("test-request"));
        IdempotentResponseWrapper response = new IdempotentResponseWrapper(new TestData("test-response"));

        repository.store(key, request);
        repository.setResponse(key, request, response);

        IdempotentRequestResponseWrapper wrapper = repository.getRequestResponseWrapper(key);

        assertNotNull(wrapper);
        assertNotNull(wrapper.getRequest());
        assertNotNull(wrapper.getResponse());
        assertEquals("test-request", ((TestData) wrapper.getRequest().getRequest()).getValue());
        assertEquals("test-response", ((TestData) wrapper.getResponse().getResponse()).getValue());
    }

    @Test
    void testSetResponse_WhenKeyExists_UpdatesResponse() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("update-response-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("test-request"));
        repository.store(key, request);

        repository.setResponse(key, request, new IdempotentResponseWrapper(new TestData("updated-response")));

        IdempotentResponseWrapper result = repository.getResponse(key);
        assertNotNull(result);
        assertEquals("updated-response", ((TestData) result.getResponse()).getValue());
    }

    @Test
    void testRemove_RemovesKeySuccessfully() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("remove-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("test-request")));

        repository.remove(key);

        assertFalse(repository.contains(key));
    }

    @Test
    void testStore_WithTTL_ExpiresCorrectly() throws Exception {
        IdempotencyKey key = new IdempotencyKey("ttl-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("ttl-request")), 1L, TimeUnit.SECONDS);

        assertTrue(repository.contains(key));
        Thread.sleep(1100);
        assertFalse(repository.contains(key));
    }

    @Test
    void testPersistReqRes_WhenFalse_DoesNotStoreData() throws RequestAlreadyExistsException {
        properties.setPersistReqRes(false);
        repository = new PostgresIdempotentRepository(entityManagerFactory, properties);

        IdempotencyKey key = new IdempotencyKey("no-persist-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("no-persist-request"));

        repository.store(key, request);

        assertTrue(repository.contains(key));

        IdempotentRequestResponseWrapper wrapper = repository.getRequestResponseWrapper(key);
        assertNotNull(wrapper);
        assertNull(wrapper.getRequest());
        assertNull(wrapper.getResponse());
    }

    @Test
    void testGetResponse_WhenResponseExists_ReturnsResponseAgain() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("response-key-2");
        TestData requestData = new TestData("request-data-2");
        TestData responseData = new TestData("response-data-2");

        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);
        IdempotentResponseWrapper response = new IdempotentResponseWrapper(responseData);

        repository.store(key, request);
        repository.setResponse(key, request, response);

        IdempotentResponseWrapper result = repository.getResponse(key);

        assertNotNull(result);
        assertEquals(responseData.getValue(), ((TestData) result.getResponse()).getValue());
    }

    @Test
    void testGetResponse_WhenResponseDoesNotExist_ReturnsNullAgain() {
        IdempotencyKey key = new IdempotencyKey("no-response-key-2");

        IdempotentResponseWrapper result = repository.getResponse(key);

        assertNull(result);
    }

    @Test
    void testGetRequestResponseWrapper_WhenDataExists_ReturnsWrapperAgain() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("wrapper-key-2");
        TestData requestData = new TestData("wrapper-request-2");
        TestData responseData = new TestData("wrapper-response-2");

        IdempotentRequestWrapper request = new IdempotentRequestWrapper(requestData);
        IdempotentResponseWrapper response = new IdempotentResponseWrapper(responseData);

        repository.store(key, request);
        repository.setResponse(key, request, response);

        IdempotentRequestResponseWrapper wrapper = repository.getRequestResponseWrapper(key);

        assertNotNull(wrapper);
        assertNotNull(wrapper.getRequest());
        assertNotNull(wrapper.getResponse());
        assertEquals(requestData.getValue(), ((TestData) wrapper.getRequest().getRequest()).getValue());
        assertEquals(responseData.getValue(), ((TestData) wrapper.getResponse().getResponse()).getValue());
    }

    @Test
    void testCleanupExpiredRecords_RemovesExpiredEntries() throws Exception {
        IdempotencyKey expiredKey = new IdempotencyKey("expired-key");
        IdempotencyKey validKey = new IdempotencyKey("valid-key");

        repository.store(expiredKey, new IdempotentRequestWrapper(new TestData("expired-request")), 1L, TimeUnit.SECONDS);
        repository.store(validKey, new IdempotentRequestWrapper(new TestData("valid-request")), 1L, TimeUnit.HOURS);

        Thread.sleep(1100);

        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = conn.createStatement()) {
            var result = statement.executeQuery("SELECT cleanup_expired_jdempotent_records()");
            result.next();
            int deletedCount = result.getInt(1);

            assertEquals(1, deletedCount);
            assertFalse(repository.contains(expiredKey));
            assertTrue(repository.contains(validKey));
        }
    }

    @Nested
    @DisplayName("Concurrency Scenarios")
    class ConcurrencyScenarios {
        
        @Test
        void testStore_ConcurrentRequests_OnlyOneSucceeds() throws Exception {
            IdempotencyKey key = new IdempotencyKey("concurrent-key");
            IdempotentRequestWrapper request1 = new IdempotentRequestWrapper(new TestData("concurrent-request-1"));
            IdempotentRequestWrapper request2 = new IdempotentRequestWrapper(new TestData("concurrent-request-2"));

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch completeLatch = new CountDownLatch(2);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger exceptionCount = new AtomicInteger(0);

            Runnable task1 = () -> runConcurrentStore(key, request1, startLatch, readyLatch, completeLatch, successCount, exceptionCount);
            Runnable task2 = () -> runConcurrentStore(key, request2, startLatch, readyLatch, completeLatch, successCount, exceptionCount);

            executor.submit(task1);
            executor.submit(task2);

            assertTrue(readyLatch.await(2, TimeUnit.SECONDS));
            startLatch.countDown();
            assertTrue(completeLatch.await(5, TimeUnit.SECONDS));

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(2, successCount.get() + exceptionCount.get(), "Total operations should be 2");
            assertEquals(1, successCount.get(), "Exactly one request should succeed");
            assertEquals(1, exceptionCount.get(), "Exactly one request should get RequestAlreadyExistsException");

            assertTrue(repository.contains(key));

            IdempotentRequestResponseWrapper wrapper = repository.getRequestResponseWrapper(key);
            assertNotNull(wrapper);
            assertNotNull(wrapper.getRequest());

            TestData storedData = (TestData) wrapper.getRequest().getRequest();
            assertTrue("concurrent-request-1".equals(storedData.getValue()) || "concurrent-request-2".equals(storedData.getValue()));
        }

        
        private void runConcurrentStore(IdempotencyKey key,
                                        IdempotentRequestWrapper request,
                                        CountDownLatch startLatch,
                                        CountDownLatch readyLatch,
                                        CountDownLatch completeLatch,
                                        AtomicInteger successCount,
                                        AtomicInteger exceptionCount) {
            // Each concurrent task uses its own repository with the shared EntityManagerFactory.
            // The repository will create EntityManager instances as needed for thread safety.
            PostgresIdempotentRepository repo = new PostgresIdempotentRepository(entityManagerFactory, copyProperties());
            try {
                readyLatch.countDown();
                startLatch.await();
                repo.store(key, request);
                successCount.incrementAndGet();
            } catch (RequestAlreadyExistsException e) {
                exceptionCount.incrementAndGet();
            } catch (Exception e) {
                exceptionCount.incrementAndGet();
            } finally {
                completeLatch.countDown();
            }
        }

        private JdempotentPostgresProperties copyProperties() {
            JdempotentPostgresProperties copy = new JdempotentPostgresProperties();
            copy.setTableName(properties.getTableName());
            copy.setPersistReqRes(properties.getPersistReqRes());
            copy.setEntityManagerBeanName(properties.getEntityManagerBeanName());
            return copy;
        }
    }
}
