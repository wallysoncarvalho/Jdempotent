package com.trendyol.jdempotent.core.datasource;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentRequestResponseWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;
import com.trendyol.jdempotent.core.model.IdempotentResponseWrapper;

/**
 * Includes all the methods of IdempotentRequestStore
 */
public abstract class AbstractIdempotentRepository implements IdempotentRepository {

    @Override
    public boolean contains(IdempotencyKey key) {
        return getMap().containsKey(key);
    }

    @Override
    public IdempotentResponseWrapper getResponse(IdempotencyKey key) {
        IdempotentRequestResponseWrapper wrapper = getMap().get(key);
        return wrapper != null ? wrapper.getResponse() : null;
    }

    @Override
    public void store(IdempotencyKey key, IdempotentRequestWrapper request) throws RequestAlreadyExistsException {
        IdempotentRequestResponseWrapper newWrapper = new IdempotentRequestResponseWrapper(request);
        IdempotentRequestResponseWrapper existing = getMap().putIfAbsent(key, newWrapper);
        if (existing != null) {
            throw new RequestAlreadyExistsException();
        }
    }

    @Override
    public void store(IdempotencyKey key, IdempotentRequestWrapper request,Long ttl, TimeUnit timeUnit) throws RequestAlreadyExistsException {
        IdempotentRequestResponseWrapper newWrapper = new IdempotentRequestResponseWrapper(request);
        IdempotentRequestResponseWrapper existing = getMap().putIfAbsent(key, newWrapper);
        if (existing != null) {
            throw new RequestAlreadyExistsException();
        }
    }

    @Override
    public void setResponse(IdempotencyKey key, IdempotentRequestWrapper request,
                            IdempotentResponseWrapper idempotentResponse) {
        getMap().computeIfPresent(key, (k, wrapper) -> {
            wrapper.setResponse(idempotentResponse);
            return wrapper;
        });
    }

    @Override
    public void setResponse(IdempotencyKey key, IdempotentRequestWrapper request,
                            IdempotentResponseWrapper idempotentResponse, Long ttl, TimeUnit timeUnit) {
        getMap().computeIfPresent(key, (k, wrapper) -> {
            wrapper.setResponse(idempotentResponse);
            return wrapper;
        });
    }

    @Override
    public void remove(IdempotencyKey key) {
        getMap().remove(key);
    }


    /**
     * @return
     */
    protected abstract ConcurrentMap<IdempotencyKey, IdempotentRequestResponseWrapper> getMap();
}
