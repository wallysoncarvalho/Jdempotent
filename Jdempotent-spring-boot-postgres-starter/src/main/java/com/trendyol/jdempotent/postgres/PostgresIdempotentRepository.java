package com.trendyol.jdempotent.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendyol.jdempotent.core.datasource.IdempotentRepository;
import com.trendyol.jdempotent.core.datasource.RequestAlreadyExistsException;
import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;
import com.trendyol.jdempotent.core.model.IdempotentResponseWrapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * PostgreSQL implementation of the IdempotentRepository interface.
 * This repository uses JPA EntityManager to store idempotent request-response data
 * in a PostgreSQL database with TTL support from @JdempotentResource annotation.
 */
public class PostgresIdempotentRepository implements IdempotentRepository {

    private static final Logger logger = LoggerFactory.getLogger(PostgresIdempotentRepository.class);

    private final EntityManager entityManager;
    private final JdempotentPostgresProperties postgresProperties;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public PostgresIdempotentRepository(EntityManager entityManager, JdempotentPostgresProperties postgresProperties, String tableName) {
        this.entityManager = entityManager;
        this.postgresProperties = postgresProperties;
        this.tableName = tableName;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean contains(IdempotencyKey key) {
        try {
            String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE idempotency_key = ?1 AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";
            
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, key.getKeyValue());
            
            Number count = (Number) query.getSingleResult();
            return count.intValue() > 0;
        } catch (Exception e) {
            logger.error("Error checking if key exists: {}", key.getKeyValue(), e);
            return false;
        }
    }

    @Override
    public IdempotentResponseWrapper getResponse(IdempotencyKey key) {
        try {
            String sql = "SELECT response_data FROM " + tableName + " WHERE idempotency_key = ?1 AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";
            
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, key.getKeyValue());
            
            String responseData = (String) query.getSingleResult();
            
            if (responseData != null && !responseData.isEmpty()) {
                Object responseObject = objectMapper.readValue(responseData, Object.class);
                return new IdempotentResponseWrapper(responseObject);
            }
            
            return null;
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            logger.error("Error getting response for key: {}", key.getKeyValue(), e);
            return null;
        }
    }

    @Override
    public IdempotentRequestResponseWrapper getRequestResponseWrapper(IdempotencyKey key) {
        try {
            String sql = "SELECT request_data, response_data FROM " + tableName + " WHERE idempotency_key = ?1 AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";
            
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, key.getKeyValue());
            
            Object[] result = (Object[]) query.getSingleResult();
            String requestData = (String) result[0];
            String responseData = (String) result[1];
            
            IdempotentRequestWrapper requestWrapper = null;
            IdempotentResponseWrapper responseWrapper = null;
            
            if (requestData != null && !requestData.isEmpty()) {
                Object requestObject = objectMapper.readValue(requestData, Object.class);
                requestWrapper = new IdempotentRequestWrapper(requestObject);
            }
            
            if (responseData != null && !responseData.isEmpty()) {
                Object responseObject = objectMapper.readValue(responseData, Object.class);
                responseWrapper = new IdempotentResponseWrapper(responseObject);
            }
            
            return new IdempotentRequestResponseWrapper(requestWrapper, responseWrapper);
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            logger.error("Error getting request-response wrapper for key: {}", key.getKeyValue(), e);
            return null;
        }
    }

    @Override
    public void store(IdempotencyKey key, IdempotentRequestWrapper requestObject) throws RequestAlreadyExistsException {
        store(key, requestObject, 0L, TimeUnit.SECONDS); // No default TTL, will be handled by annotation
    }

    @Override
    public void store(IdempotencyKey key, IdempotentRequestWrapper requestObject, Long ttl, TimeUnit timeUnit) throws RequestAlreadyExistsException {
        try {
            // Check if key already exists
            if (contains(key)) {
                throw new RequestAlreadyExistsException();
            }

            String requestData = null;
            if (postgresProperties.getPersistReqRes() && requestObject != null && requestObject.getRequest() != null) {
                requestData = objectMapper.writeValueAsString(requestObject.getRequest());
            }

            Instant expiresAt = null;
            if (ttl != null && ttl > 0) {
                long ttlSeconds = timeUnit.toSeconds(ttl);
                expiresAt = Instant.now().plusSeconds(ttlSeconds);
            }

            String sql = "INSERT INTO " + tableName + " (idempotency_key, request_data, response_data, expires_at) VALUES (?1, ?2, NULL, ?3)";

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, key.getKeyValue());
            query.setParameter(2, requestData);
            query.setParameter(3, expiresAt != null ? java.sql.Timestamp.from(expiresAt) : null);

            entityManager.getTransaction().begin();
            query.executeUpdate();
            entityManager.getTransaction().commit();

        } catch (RequestAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            logger.error("Error storing request for key: {}", key.getKeyValue(), e);
            throw new RuntimeException("Failed to store idempotent request", e);
        }
    }

    @Override
    public void remove(IdempotencyKey key) {
        try {
            String sql = "DELETE FROM " + tableName + " WHERE idempotency_key = ?1";

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, key.getKeyValue());

            entityManager.getTransaction().begin();
            query.executeUpdate();
            entityManager.getTransaction().commit();

        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            logger.error("Error removing key: {}", key.getKeyValue(), e);
        }
    }

    @Override
    public void setResponse(IdempotencyKey key, IdempotentRequestWrapper request, IdempotentResponseWrapper response) {
        setResponse(key, request, response, 0L, TimeUnit.SECONDS); // No default TTL, will be handled by annotation
    }

    @Override
    public void setResponse(IdempotencyKey key, IdempotentRequestWrapper request, IdempotentResponseWrapper response, Long ttl, TimeUnit timeUnit) {
        try {
            if (!contains(key)) {
                logger.warn("Attempting to set response for non-existent key: {}", key.getKeyValue());
                return;
            }

            String requestData = null;
            String responseData = null;

            if (postgresProperties.getPersistReqRes()) {
                if (request != null && request.getRequest() != null) {
                    requestData = objectMapper.writeValueAsString(request.getRequest());
                }
                if (response != null && response.getResponse() != null) {
                    responseData = objectMapper.writeValueAsString(response.getResponse());
                }
            }

            Instant expiresAt = null;
            if (ttl != null && ttl > 0) {
                long ttlSeconds = timeUnit.toSeconds(ttl);
                expiresAt = Instant.now().plusSeconds(ttlSeconds);
            }

            String sql = "UPDATE " + tableName + " SET request_data = ?1, response_data = ?2, expires_at = ?3 WHERE idempotency_key = ?4";

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, requestData);
            query.setParameter(2, responseData);
            query.setParameter(3, expiresAt != null ? java.sql.Timestamp.from(expiresAt) : null);
            query.setParameter(4, key.getKeyValue());

            entityManager.getTransaction().begin();
            query.executeUpdate();
            entityManager.getTransaction().commit();

        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            logger.error("Error setting response for key: {}", key.getKeyValue(), e);
        }
    }
}
