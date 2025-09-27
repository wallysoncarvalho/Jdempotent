package com.trendyol.jdempotent.core.datasource;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;

/**
 * An implementation of the idempotent AbstractIdempotentRepository
 * that uses as a default map
 */
public class InMemoryIdempotentRepository extends AbstractIdempotentRepository {

    private final ConcurrentHashMap<IdempotencyKey, IdempotentRequestResponseWrapper> map;

    public InMemoryIdempotentRepository() {
        this.map = new ConcurrentHashMap<>();
    }

    @Override
    protected ConcurrentHashMap<IdempotencyKey, IdempotentRequestResponseWrapper> getMap() {
        return map;
    }

    @Override
    public void store(IdempotencyKey key, IdempotentRequestWrapper requestObject,
            String cachePrefix, Long ttl, TimeUnit timeUnit) throws RequestAlreadyExistsException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'store'");
    }

}
