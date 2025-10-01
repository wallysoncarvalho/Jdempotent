package jdempotent.postgres.kafka;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class KafkaProcessingTracker {

    private final Map<String, Integer> processedMessages = Collections.synchronizedMap(new HashMap<>());

    public void recordInvocation(String key, String payload) {
        Integer invocationCount = processedMessages.get(key);
        if (invocationCount == null) {
            invocationCount = 0;
        }
        invocationCount++;
        processedMessages.put(key, invocationCount);
    }

    public Integer getInvocationCount(String key) {
        return processedMessages.get(key);
    }

    public Map<String, Integer> getProcessedKeys() {
        synchronized (processedMessages) {
            return Map.copyOf(processedMessages);
        }
    }

    public void reset() {
        processedMessages.clear();
    }
}


