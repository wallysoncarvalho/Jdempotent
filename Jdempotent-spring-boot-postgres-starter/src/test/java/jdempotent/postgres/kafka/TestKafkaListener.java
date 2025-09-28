package jdempotent.postgres.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendyol.jdempotent.core.annotation.JdempotentId;

@Component
public class TestKafkaListener {

    private final KafkaMessageProcessor messageProcessor;
    private final ObjectMapper objectMapper;

    public TestKafkaListener(KafkaMessageProcessor messageProcessor, ObjectMapper objectMapper) {
        this.messageProcessor = messageProcessor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "jdempotent-test-topic", groupId = "jdempotent-test-group")
    public void consume(@Payload String messageJson) {
        try {
            TestKafkaMessage message = objectMapper.readValue(messageJson, TestKafkaMessage.class);
            messageProcessor.processMessage(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize Kafka message: " + messageJson, e);
        }
    }

    public record TestKafkaMessage(@JdempotentId String idempotencyKey, String payload) implements java.io.Serializable {}
}


