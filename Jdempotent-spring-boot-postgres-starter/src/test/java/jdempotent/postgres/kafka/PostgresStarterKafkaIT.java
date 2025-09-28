package jdempotent.postgres.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;

import jdempotent.postgres.support.AbstractPostgresStarterIntegrationTest;

@SpringBootTest(classes = TestKafkaApplication.class)
@EmbeddedKafka(partitions = 1, topics = "jdempotent-test-topic", kraft = true, controlledShutdown = true)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=latest",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresStarterKafkaIT extends AbstractPostgresStarterIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaProcessingTracker tracker;

    @SuppressWarnings("unused")
    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tracker.reset();
    }

    @Test
    void shouldConsumeMessageOnceWhenSentMultipleTimes() throws Exception {
        sendMessage("kafka-1", "payload-one");
        sendMessage("kafka-1", "payload-one");

        waitForConsumption();

        assertThat(tracker.getInvocationCount()).isEqualTo(1);
        assertThat(tracker.getProcessedMessages()).containsEntry("kafka-1", "payload-one");
    }

    @Test
    void shouldThrowPayloadConflictWhenPayloadDiffers() throws Exception {
        sendMessage("kafka-2", "payload-a");
        waitForConsumption();

        int beforeCount = tracker.getInvocationCount();

        sendMessage("kafka-2", "payload-b");
        waitForConsumption();

        assertThat(tracker.getInvocationCount()).isEqualTo(beforeCount);
        assertThat(tracker.getProcessedMessages()).containsEntry("kafka-2", "payload-a");
    }

    private void sendMessage(String key, String payload) throws Exception {
        TestKafkaListener.TestKafkaMessage message = new TestKafkaListener.TestKafkaMessage(key, payload);
        ProducerRecord<String, String> record = new ProducerRecord<>("jdempotent-test-topic", key,
                objectMapper.writeValueAsString(message));
        kafkaTemplate.send(record).get();
    }

    private void waitForConsumption() throws InterruptedException {
        Thread.sleep(Duration.ofSeconds(2).toMillis());
    }
}
