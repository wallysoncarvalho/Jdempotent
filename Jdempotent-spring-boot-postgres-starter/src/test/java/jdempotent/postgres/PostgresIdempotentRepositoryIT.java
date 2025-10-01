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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.trendyol.jdempotent.core.datasource.RequestAlreadyExistsException;
import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;
import com.trendyol.jdempotent.core.model.IdempotentResponseWrapper;
import com.trendyol.jdempotent.postgres.JdempotentPostgresProperties;
import com.trendyol.jdempotent.postgres.PostgresIdempotentRepository;

import jakarta.persistence.EntityManagerFactory;
import jdempotent.postgres.config.TestPostgresConfig;
import jdempotent.postgres.support.AbstractPostgresStarterIntegrationTest;
import jdempotent.postgres.support.TestData;

@SpringBootTest(classes = {TestPostgresConfig.class})
class PostgresIdempotentRepositoryIT extends AbstractPostgresStarterIntegrationTest {

    @Autowired
    private PostgresIdempotentRepository repository;

    @Autowired
    private JdempotentPostgresProperties properties;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void resetProperties() {
        // Reset properties to default values after each test to prevent test interference
        properties.setPersistReqRes(true);
        // Recreate repository with reset properties if it was modified
        repository = new PostgresIdempotentRepository(entityManagerFactory, properties);
    }

    @Test
    void test_store_when_key_does_not_exist_stores_successfully() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("test-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("test-request"));

        repository.store(key, request);

        assertTrue(repository.contains(key));
    }

    @Test
    void test_store_when_key_exists_throws_exception() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("duplicate-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("request-1")));

        assertThrows(RequestAlreadyExistsException.class,
                () -> repository.store(key, new IdempotentRequestWrapper(new TestData("request-2"))));
    }

    @Test
    void test_contains_when_key_exists_returns_true() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("existing-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("test-request")));

        assertTrue(repository.contains(key));
    }

    @Test
    void test_contains_when_key_does_not_exist_returns_false() {
        assertFalse(repository.contains(new IdempotencyKey("non-existing-key")));
    }

    @Test
    void test_get_response_when_response_exists_returns_response() throws RequestAlreadyExistsException {
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
    void test_get_response_when_response_does_not_exist_returns_null() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("no-response-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("test-request")));

        assertNull(repository.getResponse(key));
    }

    @Test
    void test_get_request_response_wrapper_when_data_exists_returns_wrapper() throws RequestAlreadyExistsException {
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
    void test_set_response_when_key_exists_updates_response() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("update-response-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper(new TestData("test-request"));
        repository.store(key, request);

        repository.setResponse(key, request, new IdempotentResponseWrapper(new TestData("updated-response")));

        IdempotentResponseWrapper result = repository.getResponse(key);
        assertNotNull(result);
        assertEquals("updated-response", ((TestData) result.getResponse()).getValue());
    }

    @Test
    void test_remove_removes_key_successfully() throws RequestAlreadyExistsException {
        IdempotencyKey key = new IdempotencyKey("remove-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("test-request")));

        repository.remove(key);

        assertFalse(repository.contains(key));
    }

    @Test
    void test_store_with_ttl_expires_correctly() throws Exception {
        IdempotencyKey key = new IdempotencyKey("ttl-key");
        repository.store(key, new IdempotentRequestWrapper(new TestData("ttl-request")), 1L, TimeUnit.SECONDS);

        assertTrue(repository.contains(key));
        Thread.sleep(1100);
        assertFalse(repository.contains(key));
    }

    @Test
    void test_persist_req_res_when_false_does_not_store_data() throws RequestAlreadyExistsException {
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
    void test_get_response_when_response_exists_returns_response_again() throws RequestAlreadyExistsException {
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
    void test_get_response_when_response_does_not_exist_returns_null_again() {
        IdempotencyKey key = new IdempotencyKey("no-response-key-2");

        IdempotentResponseWrapper result = repository.getResponse(key);

        assertNull(result);
    }

    @Test
    void test_get_request_response_wrapper_when_data_exists_returns_wrapper_again() throws RequestAlreadyExistsException {
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
    void test_cleanup_expired_records_removes_expired_entries() throws Exception {
        IdempotencyKey expiredKey = new IdempotencyKey("expired-key");
        IdempotencyKey validKey = new IdempotencyKey("valid-key");

        repository.store(expiredKey, new IdempotentRequestWrapper(new TestData("expired-request")), 1L, TimeUnit.SECONDS);
        repository.store(validKey, new IdempotentRequestWrapper(new TestData("valid-request")), 1L, TimeUnit.HOURS);

        Thread.sleep(1100);

        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = conn.createStatement()) {
            var result = statement.executeQuery("SELECT cleanup_expired_jdempotent_records(100)");
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
        void test_store_concurrent_requests_only_one_succeeds() throws Exception {
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
