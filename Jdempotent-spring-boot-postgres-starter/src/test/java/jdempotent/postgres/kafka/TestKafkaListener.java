package jdempotent.postgres.kafka;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendyol.jdempotent.core.annotation.JdempotentId;
import com.trendyol.jdempotent.core.annotation.JdempotentRequestPayload;
import com.trendyol.jdempotent.core.annotation.JdempotentResource;

@Component
public class TestKafkaListener {

    private final KafkaProcessingTracker tracker;
    private final ObjectMapper objectMapper;

    public TestKafkaListener(KafkaProcessingTracker tracker, ObjectMapper objectMapper) {
        this.tracker = tracker;
        this.objectMapper = objectMapper;
    }

    @JdempotentResource(cachePrefix = "kafka", ttl = 10, ttlTimeUnit = TimeUnit.MINUTES)
    @KafkaListener(topics = "jdempotent-test-topic", groupId = "jdempotent-test-group", clientIdPrefix = "jdempotent-test-group")
    public void consume(@JdempotentRequestPayload @Payload String messageJson)
            throws JsonProcessingException {
        TestKafkaMessage message = objectMapper.readValue(messageJson, TestKafkaMessage.class);
        tracker.recordInvocation(message.idempotencyKey(), message.payload());
    }

    @JdempotentResource(cachePrefix = "kafka2", ttl = 10, ttlTimeUnit = TimeUnit.MINUTES)
    @KafkaListener(topics = "jdempotent-test-topic-2", groupId = "jdempotent-test-group-2", clientIdPrefix = "jdempotent-test-group-2")
    public void consume(@JdempotentId @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @JdempotentRequestPayload @Payload TestKafkaMessagePayload payload) {
        tracker.recordInvocation(key, payload.payload());
    }

    public record TestKafkaMessage(@JdempotentId String idempotencyKey, String payload)
            implements java.io.Serializable {}

    public record TestKafkaMessagePayload(String payload) implements java.io.Serializable {}
}
