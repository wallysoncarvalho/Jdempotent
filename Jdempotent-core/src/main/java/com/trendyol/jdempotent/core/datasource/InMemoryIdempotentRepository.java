package com.trendyol.jdempotent.core.datasource;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;

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
    protected ConcurrentMap<IdempotencyKey, IdempotentRequestResponseWrapper> getMap() {
        return map;
    }

}
