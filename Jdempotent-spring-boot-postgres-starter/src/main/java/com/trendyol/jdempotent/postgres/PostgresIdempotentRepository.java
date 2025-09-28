package com.trendyol.jdempotent.postgres;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trendyol.jdempotent.core.datasource.IdempotentRepository;
import com.trendyol.jdempotent.core.datasource.RequestAlreadyExistsException;
import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;
import com.trendyol.jdempotent.core.model.IdempotentResponseWrapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;

/**
 * PostgreSQL implementation of the IdempotentRepository interface.
 * This repository uses JPA EntityManager to store idempotent request-response data
 * in a PostgreSQL database with TTL support from @JdempotentResource annotation.
 * 
 * <p>Data is stored as byte arrays for efficiency and to avoid JSON encoding issues.</p>
 */
public class PostgresIdempotentRepository implements IdempotentRepository {

    private static final Logger logger = LoggerFactory.getLogger(PostgresIdempotentRepository.class);

    private final EntityManagerFactory entityManagerFactory;
    private final JdempotentPostgresProperties postgresProperties;

    public PostgresIdempotentRepository(EntityManagerFactory entityManagerFactory, JdempotentPostgresProperties postgresProperties) {
        this.entityManagerFactory = entityManagerFactory;
        this.postgresProperties = postgresProperties;
    }

    @Override
    public boolean contains(IdempotencyKey key) {
        return executeWithEntityManager(entityManager -> {
            try {
                String sql = "SELECT COUNT(*) FROM " + postgresProperties.getTableName() + " WHERE idempotency_key = ?1 AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";
                
                Query query = entityManager.createNativeQuery(sql);
                query.setParameter(1, key.getKeyValue());
                
                Number count = (Number) query.getSingleResult();
                return count.intValue() > 0;
            } catch (Exception e) {
                logger.error("Error checking if key exists: {}", key.getKeyValue(), e);
                return false;
            }
        });
    }

    @Override
    public IdempotentResponseWrapper getResponse(IdempotencyKey key) {
        return executeWithEntityManager(entityManager -> {
            try {
                String sql = "SELECT response_data FROM " + postgresProperties.getTableName() + " WHERE idempotency_key = ?1 AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";
                
                Query query = entityManager.createNativeQuery(sql);
                query.setParameter(1, key.getKeyValue());
                
                byte[] responseData = (byte[]) query.getSingleResult();
                
                if (responseData != null && responseData.length > 0) {
                    Object responseObject = deserializeFromBytes(responseData);
                    return new IdempotentResponseWrapper(responseObject);
                }
                
                return null;
            } catch (NoResultException e) {
                return null;
            } catch (Exception e) {
                logger.error("Error getting response for key: {}", key.getKeyValue(), e);
                return null;
            }
        });
    }

    @Override
    public IdempotentRequestResponseWrapper getRequestResponseWrapper(IdempotencyKey key) {
        return executeWithEntityManager(entityManager -> {
            try {
                String sql = "SELECT request_data, response_data FROM " + postgresProperties.getTableName() + " WHERE idempotency_key = ?1 AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";
                
                Query query = entityManager.createNativeQuery(sql);
                query.setParameter(1, key.getKeyValue());
                
                Object[] result = (Object[]) query.getSingleResult();
                byte[] requestData = (byte[]) result[0];
                byte[] responseData = (byte[]) result[1];
                
                IdempotentRequestWrapper requestWrapper = null;
                IdempotentResponseWrapper responseWrapper = null;
                
                if (requestData != null && requestData.length > 0) {
                    Object requestObject = deserializeFromBytes(requestData);
                    requestWrapper = new IdempotentRequestWrapper(requestObject);
                }
                
                if (responseData != null && responseData.length > 0) {
                    Object responseObject = deserializeFromBytes(responseData);
                    responseWrapper = new IdempotentResponseWrapper(responseObject);
                }
                
                return new IdempotentRequestResponseWrapper(requestWrapper, responseWrapper);
            } catch (NoResultException e) {
                return null;
            } catch (Exception e) {
                logger.error("Error getting request-response wrapper for key: {}", key.getKeyValue(), e);
                return null;
            }
        });
    }

    @Override
    public void store(IdempotencyKey key, IdempotentRequestWrapper requestObject) throws RequestAlreadyExistsException {
        store(key, requestObject, 0L, TimeUnit.SECONDS); // No default TTL, will be handled by annotation
    }

    @Override
    public void store(IdempotencyKey key, IdempotentRequestWrapper requestObject, Long ttl, TimeUnit timeUnit) throws RequestAlreadyExistsException {
        store(key, requestObject, null, ttl, timeUnit);
    }

    @Override
    public void store(IdempotencyKey key, IdempotentRequestWrapper requestObject, String cachePrefix, Long ttl, TimeUnit timeUnit) throws RequestAlreadyExistsException {
        try {
            executeWithTransactionForStore(entityManager -> {
                byte[] requestData = null;
                if (postgresProperties.getPersistReqRes() && requestObject != null && requestObject.getRequest() != null) {
                    requestData = serializeToBytes(requestObject.getRequest());
                }

                Instant expiresAt = null;
                if (ttl != null && ttl > 0) {
                    long ttlSeconds = timeUnit.toSeconds(ttl);
                    expiresAt = Instant.now().plusSeconds(ttlSeconds);
                }

                // Use INSERT ... ON CONFLICT to handle concurrency safely
                // If the key already exists, we'll get 0 rows affected and throw RequestAlreadyExistsException
                String sql = "INSERT INTO " + postgresProperties.getTableName() + 
                    " (idempotency_key, cache_prefix, request_data, response_data, expires_at) VALUES (?1, ?2, ?3, NULL, ?4)" +
                    " ON CONFLICT (idempotency_key) DO NOTHING";

                Query query = entityManager.createNativeQuery(sql);
                query.setParameter(1, key.getKeyValue());
                query.setParameter(2, cachePrefix);
                query.setParameter(3, requestData);
                query.setParameter(4, expiresAt != null ? java.sql.Timestamp.from(expiresAt) : null);

                int rowsAffected = query.executeUpdate();

                // If no rows were inserted, it means the key already exists
                if (rowsAffected == 0) {
                    throw new RequestAlreadyExistsException();
                }

                return null; // Void operation
            });
        } catch (RequestAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error storing request for key: {}", key.getKeyValue(), e);
            throw new RuntimeException("Failed to store idempotent request", e);
        }
    }

    @Override
    public void remove(IdempotencyKey key) {
        executeWithTransaction(entityManager -> {
            try {
                String sql = "DELETE FROM " + postgresProperties.getTableName() + " WHERE idempotency_key = ?1";

                Query query = entityManager.createNativeQuery(sql);
                query.setParameter(1, key.getKeyValue());

                query.executeUpdate();
                return null; // Void operation
            } catch (Exception e) {
                logger.error("Error removing key: {}", key.getKeyValue(), e);
                throw e;
            }
        });
    }

    @Override
    public void setResponse(IdempotencyKey key, IdempotentRequestWrapper request, IdempotentResponseWrapper response) {
        setResponse(key, request, response, 0L, TimeUnit.SECONDS); // No default TTL, will be handled by annotation
    }

    @Override
    public void setResponse(IdempotencyKey key, IdempotentRequestWrapper request, IdempotentResponseWrapper response, Long ttl, TimeUnit timeUnit) {
        executeWithTransaction(entityManager -> {
            try {
                if (!contains(key)) {
                    logger.warn("Attempting to set response for non-existent key: {}", key.getKeyValue());
                    return null;
                }

                byte[] requestData = null;
                byte[] responseData = null;

                logger.debug("PostgresIdempotentRepository.setResponse() - persistReqRes setting: {}", postgresProperties.getPersistReqRes());
                
                if (postgresProperties.getPersistReqRes()) {
                    if (request != null && request.getRequest() != null) {
                        requestData = serializeToBytes(request.getRequest());
                    }
                    if (response != null && response.getResponse() != null) {
                        responseData = serializeToBytes(response.getResponse());
                    }
                }

                Instant expiresAt = null;
                if (ttl != null && ttl > 0) {
                    long ttlSeconds = timeUnit.toSeconds(ttl);
                    expiresAt = Instant.now().plusSeconds(ttlSeconds);
                }

                String sql = "UPDATE " + postgresProperties.getTableName() + " SET request_data = ?1, response_data = ?2, expires_at = ?3 WHERE idempotency_key = ?4";

                Query query = entityManager.createNativeQuery(sql);
                query.setParameter(1, requestData);
                query.setParameter(2, responseData);
                query.setParameter(3, expiresAt != null ? java.sql.Timestamp.from(expiresAt) : null);
                query.setParameter(4, key.getKeyValue());

                query.executeUpdate();
                return null; // Void operation
            } catch (Exception e) {
                logger.error("Error setting response for key: {}", key.getKeyValue(), e);
                throw e;
            }
        });
    }

    /**
     * Serializes an object to byte array using Java serialization.
     * 
     * @param object the object to serialize
     * @return byte array representation of the object
     * @throws RuntimeException if serialization fails
     */
    private byte[] serializeToBytes(Object object) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            return baos.toByteArray();
        } catch (IOException e) {
            logger.error("Error serializing object to bytes", e);
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    /**
     * Deserializes a byte array back to an object using Java deserialization.
     * 
     * @param bytes the byte array to deserialize
     * @return the deserialized object
     * @throws RuntimeException if deserialization fails
     */
    private Object deserializeFromBytes(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Error deserializing object from bytes", e);
            throw new RuntimeException("Failed to deserialize object", e);
        }
    }

    /**
     * Executes a database operation with proper EntityManager lifecycle management.
     * Creates a new EntityManager, executes the operation, and ensures cleanup.
     * 
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    private <T> T executeWithEntityManager(EntityManagerOperation<T> operation) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            return operation.execute(entityManager);
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    /**
     * Executes a database operation that requires a transaction with proper EntityManager lifecycle management.
     * Creates a new EntityManager, begins a transaction, executes the operation, commits, and ensures cleanup.
     * 
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    private <T> T executeWithTransaction(EntityManagerOperation<T> operation) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            T result = operation.execute(entityManager);
            entityManager.getTransaction().commit();
            return result;
        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw e;
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    /**
     * Executes a database operation that requires a transaction and can throw RequestAlreadyExistsException.
     * Creates a new EntityManager, begins a transaction, executes the operation, commits, and ensures cleanup.
     * 
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws RequestAlreadyExistsException if the operation throws this exception
     */
    private <T> T executeWithTransactionForStore(EntityManagerStoreOperation<T> operation) throws RequestAlreadyExistsException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            T result = operation.execute(entityManager);
            entityManager.getTransaction().commit();
            return result;
        } catch (RequestAlreadyExistsException e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw e;
        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw e;
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    /**
     * Functional interface for EntityManager operations.
     * 
     * @param <T> the return type
     */
    @FunctionalInterface
    private interface EntityManagerOperation<T> {
        T execute(EntityManager entityManager);
    }

    /**
     * Functional interface for EntityManager operations that can throw RequestAlreadyExistsException.
     * 
     * @param <T> the return type
     */
    @FunctionalInterface
    private interface EntityManagerStoreOperation<T> {
        T execute(EntityManager entityManager) throws RequestAlreadyExistsException;
    }
}
