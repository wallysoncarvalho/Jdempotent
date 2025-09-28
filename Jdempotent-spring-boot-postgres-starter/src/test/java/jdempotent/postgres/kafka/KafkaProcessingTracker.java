package jdempotent.postgres.kafka;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class KafkaProcessingTracker {

    private final AtomicInteger invocationCount = new AtomicInteger();
    private final Map<String, String> processedMessages = Collections.synchronizedMap(new HashMap<>());

    public void recordInvocation(String key, String payload) {
        invocationCount.incrementAndGet();
        processedMessages.put(key, payload);
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    public Map<String, String> getProcessedMessages() {
        synchronized (processedMessages) {
            return Map.copyOf(processedMessages);
        }
    }

    public void reset() {
        invocationCount.set(0);
        processedMessages.clear();
    }
}


