package jdempotent.postgres.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(partitions = 1, topics = {
        "jdempotent-test-topic",
        "jdempotent-test-topic-2" }, kraft = true, controlledShutdown = true)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=latest",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer" })
class PostgresStarterKafkaIT extends AbstractPostgresStarterIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaProcessingTracker tracker;

    @Autowired
    @SuppressWarnings("unused")
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tracker.reset();
    }

    @Test
    void should_consume_message_once_when_sent_multiple_times() throws Exception {
        sendMessage("kafka-1", "payload-one", "jdempotent-test-topic");
        sendMessage("kafka-1", "payload-one", "jdempotent-test-topic");

        waitForConsumption();

        assertEquals(1, tracker.getInvocationCount("kafka-1"));
    }

    @Test
    void should_throw_payload_conflict_when_payload_differs() throws Exception {
        sendMessage("kafka-2", "payload-a", "jdempotent-test-topic-2");
        sendMessage("kafka-2", "payload-b", "jdempotent-test-topic-2");

        waitForConsumption();

        assertEquals(1, tracker.getInvocationCount("kafka-2"));
    }

    private void sendMessage(String key, String payload, String topico) throws Exception {
        TestKafkaListener.TestKafkaMessage message = new TestKafkaListener.TestKafkaMessage(key,
                payload);
        ProducerRecord<String, String> record = new ProducerRecord<>(topico, key,
                objectMapper.writeValueAsString(message));
        kafkaTemplate.send(record).get();
    }

    private void waitForConsumption() throws InterruptedException {
        Thread.sleep(Duration.ofSeconds(2).toMillis());
    }
}
