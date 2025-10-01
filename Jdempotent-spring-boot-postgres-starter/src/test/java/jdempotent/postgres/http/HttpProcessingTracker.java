package jdempotent.postgres.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class HttpProcessingTracker {

    private final Map<String, Integer> invocationCounts = Collections.synchronizedMap(new HashMap<>());

    public void recordInvocation(String idempotencyKey) {
        invocationCounts.merge(idempotencyKey, 1, Integer::sum);
    }

    public int getInvocationCount(String idempotencyKey) {
        return invocationCounts.getOrDefault(idempotencyKey, 0);
    }

    public int getTotalInvocationCount() {
        synchronized (invocationCounts) {
            return invocationCounts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public Map<String, Integer> getAllInvocationCounts() {
        synchronized (invocationCounts) {
            return Map.copyOf(invocationCounts);
        }
    }

    public void reset() {
        invocationCounts.clear();
    }
}
