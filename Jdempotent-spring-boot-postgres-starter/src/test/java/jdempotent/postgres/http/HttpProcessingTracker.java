package jdempotent.postgres.http;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class HttpProcessingTracker {

    private final AtomicInteger invocationCount = new AtomicInteger();
    private final Set<String> processedIds = Collections.synchronizedSet(new HashSet<>());

    public void recordInvocation(String id) {
        invocationCount.incrementAndGet();
        processedIds.add(id);
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    public Set<String> getProcessedIds() {
        synchronized (processedIds) {
            return Set.copyOf(processedIds);
        }
    }

    public void reset() {
        invocationCount.set(0);
        processedIds.clear();
    }
}


