package jdempotent.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trendyol.jdempotent.core.datasource.RequestAlreadyExistsException;
import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;
import com.trendyol.jdempotent.core.model.IdempotentResponseWrapper;
import com.trendyol.jdempotent.postgres.JdempotentPostgresProperties;
import com.trendyol.jdempotent.postgres.PostgresIdempotentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class PostgresIdempotentRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private EntityTransaction transaction;

    @Mock
    private Query query;

    private JdempotentPostgresProperties properties;
    private PostgresIdempotentRepository repository;

    @BeforeEach
    void setUp() {
        properties = new JdempotentPostgresProperties();
        properties.setTableName("jdempotent");
        properties.setPersistReqRes(true);

        repository = new PostgresIdempotentRepository(entityManager, properties);
    }

    @Test
    void testContains_WhenKeyExists_ReturnsTrue() {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);

        // When
        boolean result = repository.contains(key);

        // Then
        assertTrue(result);
        verify(entityManager).createNativeQuery(contains("SELECT COUNT(*)"));
        verify(query).setParameter(1, "test-key");
    }

    @Test
    void testContains_WhenKeyDoesNotExist_ReturnsFalse() {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0);

        // When
        boolean result = repository.contains(key);

        // Then
        assertFalse(result);
    }

    @Test
    void testStore_WhenKeyDoesNotExist_StoresSuccessfully() throws RequestAlreadyExistsException {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper("test-request");
        
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(entityManager.getTransaction()).thenReturn(transaction);
        // Mock executeUpdate to return 1 (one row affected - successful insert)
        when(query.executeUpdate()).thenReturn(1);

        // When
        repository.store(key, request, 3600L, TimeUnit.SECONDS);

        // Then
        verify(transaction).begin();
        verify(query).executeUpdate();
        verify(transaction).commit();
    }

    @Test
    void testStore_WhenKeyExists_ThrowsException() {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper("test-request");
        
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(entityManager.getTransaction()).thenReturn(transaction);
        // Mock executeUpdate to return 0 (no rows affected - key already exists)
        when(query.executeUpdate()).thenReturn(0);

        // When & Then
        assertThrows(RequestAlreadyExistsException.class, () -> {
            repository.store(key, request, 3600L, TimeUnit.SECONDS);
        });
    }

    @Test
    void testGetResponse_WhenResponseExists_ReturnsResponse() {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        // Simulate serialized byte array for "test-response" string
        String testResponse = "test-response";
        byte[] responseBytes = serializeTestObject(testResponse);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(responseBytes);

        // When
        IdempotentResponseWrapper result = repository.getResponse(key);

        // Then
        assertNotNull(result);
        assertEquals("test-response", result.getResponse());
    }

    @Test
    void testGetRequestResponseWrapper_WhenDataExists_ReturnsWrapper() {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        byte[] requestBytes = serializeTestObject("test-request");
        byte[] responseBytes = serializeTestObject("test-response");
        Object[] resultArray = {requestBytes, responseBytes};
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(resultArray);

        // When
        IdempotentRequestResponseWrapper result = repository.getRequestResponseWrapper(key);

        // Then
        assertNotNull(result);
        assertNotNull(result.getRequest());
        assertNotNull(result.getResponse());
        assertEquals("test-request", result.getRequest().getRequest());
        assertEquals("test-response", result.getResponse().getResponse());
    }

    @Test
    void testRemove_RemovesKeySuccessfully() {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(entityManager.getTransaction()).thenReturn(transaction);
        when(query.executeUpdate()).thenReturn(1);

        // When
        repository.remove(key);

        // Then
        verify(transaction).begin();
        verify(query).executeUpdate();
        verify(transaction).commit();
        verify(entityManager).createNativeQuery(contains("DELETE FROM"));
    }

    @Test
    void testSetResponse_WhenKeyExists_UpdatesResponse() {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper("test-request");
        IdempotentResponseWrapper response = new IdempotentResponseWrapper("test-response");
        
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(entityManager.getTransaction()).thenReturn(transaction);
        // Mock contains() to return true
        when(query.getSingleResult()).thenReturn(1);
        when(query.executeUpdate()).thenReturn(1);

        // When
        repository.setResponse(key, request, response, 3600L, TimeUnit.SECONDS);

        // Then
        verify(transaction).begin();
        verify(query).executeUpdate();
        verify(transaction).commit();
        verify(entityManager).createNativeQuery(contains("UPDATE"));
    }

    @Test
    void testStore_WithPersistReqResFalse_DoesNotStoreRequestData() throws RequestAlreadyExistsException {
        // Given
        properties.setPersistReqRes(false);
        repository = new PostgresIdempotentRepository(entityManager, properties);
        
        IdempotencyKey key = new IdempotencyKey("test-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper("test-request");
        
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(entityManager.getTransaction()).thenReturn(transaction);
        when(query.executeUpdate()).thenReturn(1);

        // When
        repository.store(key, request, 3600L, TimeUnit.SECONDS);

        // Then
        verify(query).setParameter(eq(3), eq(null)); // Updated parameter index due to cache_prefix column
    }

    @Test
    void testStore_WithCachePrefix_StoresCachePrefixCorrectly() throws RequestAlreadyExistsException {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper("test-request");
        String cachePrefix = "user-service";
        
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(entityManager.getTransaction()).thenReturn(transaction);
        when(query.executeUpdate()).thenReturn(1);

        // When
        repository.store(key, request, cachePrefix, 3600L, TimeUnit.SECONDS);

        // Then
        verify(entityManager).createNativeQuery(contains("cache_prefix"));
        verify(query).setParameter(eq(1), eq("test-key")); // idempotency_key
        verify(query).setParameter(eq(2), eq(cachePrefix)); // cache_prefix
        verify(query).setParameter(eq(3), any(byte[].class)); // request_data
        verify(query).setParameter(eq(4), any(java.sql.Timestamp.class)); // expires_at
        verify(transaction).begin();
        verify(query).executeUpdate();
        verify(transaction).commit();
    }

    @Test
    void testStore_WithNullCachePrefix_StoresNullCachePrefix() throws RequestAlreadyExistsException {
        // Given
        IdempotencyKey key = new IdempotencyKey("test-key");
        IdempotentRequestWrapper request = new IdempotentRequestWrapper("test-request");
        
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(entityManager.getTransaction()).thenReturn(transaction);
        when(query.executeUpdate()).thenReturn(1);

        // When
        repository.store(key, request, null, 3600L, TimeUnit.SECONDS);

        // Then
        verify(query).setParameter(eq(2), eq(null)); // cache_prefix should be null
    }

    /**
     * Helper method to serialize objects for testing
     */
    private byte[] serializeTestObject(Object obj) {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to serialize test object", e);
        }
    }
}
