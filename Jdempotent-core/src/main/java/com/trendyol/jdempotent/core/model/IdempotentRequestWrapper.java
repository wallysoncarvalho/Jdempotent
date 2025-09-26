package com.trendyol.jdempotent.core.model;

import java.io.Serializable;

/**
 *
 * Wraps the incoming event value
 *
 */
@SuppressWarnings("serial")
public class IdempotentRequestWrapper implements Serializable {
    private Object request;

    public IdempotentRequestWrapper(){
    }

    public IdempotentRequestWrapper(Object request) {
        this.request = request;
    }

    public Object getRequest() {
        return request;
    }

    @Override
    public int hashCode() {
        return request == null ? 0 : request.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        IdempotentRequestWrapper that = (IdempotentRequestWrapper) obj;
        return request != null ? request.equals(that.request) : that.request == null;
    }

    @Override
    public String toString() {
        return String.format("IdempotentRequestWrapper [request=%s]", request);
    }
}
