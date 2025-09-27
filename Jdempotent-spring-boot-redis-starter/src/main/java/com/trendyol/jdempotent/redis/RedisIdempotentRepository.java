package com.trendyol.jdempotent.redis;

import com.trendyol.jdempotent.core.datasource.IdempotentRepository;
import com.trendyol.jdempotent.core.datasource.RequestAlreadyExistsException;
import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;
import com.trendyol.jdempotent.core.model.IdempotentResponseWrapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

/**
 *
 * An implementation of the idempotent IdempotentRepository
 * that uses a distributed hash map from Redis
 *
 * That repository needs to store idempotent hash for idempotency check
 *
 */
public class RedisIdempotentRepository implements IdempotentRepository {

    private final ValueOperations<String, IdempotentRequestResponseWrapper> valueOperations;
    private final RedisTemplate redisTemplate;
    private final RedisConfigProperties redisProperties;


    public RedisIdempotentRepository(RedisTemplate redisTemplate, RedisConfigProperties redisProperties) {
        valueOperations = redisTemplate.opsForValue();
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
    }

    @Override
    public boolean contains(IdempotencyKey idempotencyKey) {
        return valueOperations.get(idempotencyKey.getKeyValue()) != null;
    }

    @Override
    public IdempotentResponseWrapper getResponse(IdempotencyKey idempotencyKey) {
        IdempotentRequestResponseWrapper wrapper = valueOperations.get(idempotencyKey.getKeyValue());
        return wrapper != null ? wrapper.getResponse() : null;
    }

    @Override
    @Deprecated
    public void store(IdempotencyKey idempotencyKey, IdempotentRequestWrapper request) throws RequestAlreadyExistsException {
        Boolean set = valueOperations.setIfAbsent(
                idempotencyKey.getKeyValue(),
                prepareValue(request),
                redisProperties.getExpirationTimeHour(),
                TimeUnit.HOURS
        );
        if (Boolean.FALSE.equals(set)) {
            throw new RequestAlreadyExistsException();
        }
    }

    @Override
    public void store(IdempotencyKey idempotencyKey, IdempotentRequestWrapper request, Long ttl, TimeUnit timeUnit) throws RequestAlreadyExistsException {
        store(idempotencyKey, request, null, ttl, timeUnit);
    }

    @Override
    public void store(IdempotencyKey idempotencyKey, IdempotentRequestWrapper request, String cachePrefix, Long ttl, TimeUnit timeUnit) throws RequestAlreadyExistsException {
        ttl = ttl == 0 ? redisProperties.getExpirationTimeHour() : ttl;
        
        // For Redis, we store the cache prefix as part of the value since Redis is key-value based
        // The cache prefix is mainly for PostgreSQL where we can store it as a separate column
        Boolean set = valueOperations.setIfAbsent(
                idempotencyKey.getKeyValue(),
                prepareValue(request),
                ttl,
                timeUnit
        );
        if (Boolean.FALSE.equals(set)) {
            throw new RequestAlreadyExistsException();
        }
    }

    @Override
    public void remove(IdempotencyKey idempotencyKey) {
        redisTemplate.delete(idempotencyKey.getKeyValue());
    }

    @Override
    @Deprecated
    public void setResponse(IdempotencyKey idempotencyKey, IdempotentRequestWrapper request, IdempotentResponseWrapper response) {
        if (contains(idempotencyKey)) {
            IdempotentRequestResponseWrapper requestResponseWrapper = valueOperations.get(idempotencyKey.getKeyValue());
            if (requestResponseWrapper != null) {
                requestResponseWrapper.setResponse(response);
                valueOperations.set(idempotencyKey.getKeyValue(), prepareValue(request), redisProperties.getExpirationTimeHour(), TimeUnit.HOURS);
            }
        }
    }

    /**
     * ttl describe
     *
     * @param idempotencyKey
     * @param request
     * @param response
     * @param ttl
     */
    @Override
    public void setResponse(IdempotencyKey idempotencyKey, IdempotentRequestWrapper request, IdempotentResponseWrapper response, Long ttl, TimeUnit timeUnit) {
        if (contains(idempotencyKey)) {
            ttl = ttl == 0 ? redisProperties.getExpirationTimeHour() : ttl;
            IdempotentRequestResponseWrapper requestResponseWrapper = valueOperations.get(idempotencyKey.getKeyValue());
            if (requestResponseWrapper != null) {
                requestResponseWrapper.setResponse(response);
                valueOperations.set(idempotencyKey.getKeyValue(), prepareValue(request, response), ttl, timeUnit);
            }
        }
    }

    /**
     * Prepares the value stored in redis
     *
     * if persistReqRes set to false,
     * it does not persist related request values in redis
     * @param request
     * @return
     */
    private IdempotentRequestResponseWrapper prepareValue(IdempotentRequestWrapper request) {
        if (redisProperties.getPersistReqRes()) {
            return new IdempotentRequestResponseWrapper(request);
        }
        return new IdempotentRequestResponseWrapper(null);
    }

    /**
     * Prepares the value stored in redis
     *
     * if persistReqRes set to false,
     * it does not persist related request and response values in redis
     * @param request
     * @param response
     * @return
     */
    private IdempotentRequestResponseWrapper prepareValue(IdempotentRequestWrapper request, IdempotentResponseWrapper response) {
        if (redisProperties.getPersistReqRes()) {
            return new IdempotentRequestResponseWrapper(request, response);
        }
        return new IdempotentRequestResponseWrapper(null);
    }

    @Override
    public IdempotentRequestResponseWrapper getRequestResponseWrapper(IdempotencyKey key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRequestResponseWrapper'");
    }
}
