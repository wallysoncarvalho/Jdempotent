package jdempotent.postgres.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import jdempotent.postgres.config.TestKafkaConsumerConfig;
import jdempotent.postgres.config.TestKafkaProducerConfig;

@SpringBootApplication
@Import({TestKafkaConsumerConfig.class, TestKafkaProducerConfig.class})
public class TestKafkaApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestKafkaApplication.class, args);
    }
}
