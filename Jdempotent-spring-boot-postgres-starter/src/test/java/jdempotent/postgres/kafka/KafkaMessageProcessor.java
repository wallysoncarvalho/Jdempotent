package jdempotent.postgres.kafka;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trendyol.jdempotent.core.annotation.JdempotentRequestPayload;
import com.trendyol.jdempotent.core.annotation.JdempotentResource;

@Service
public class KafkaMessageProcessor {

    private final KafkaProcessingTracker tracker;

    public KafkaMessageProcessor(KafkaProcessingTracker tracker) {
        this.tracker = tracker;
    }

    @JdempotentResource(cachePrefix = "kafka", ttl = 10, ttlTimeUnit = TimeUnit.MINUTES)
    @Transactional
    public void processMessage(@JdempotentRequestPayload TestKafkaListener.TestKafkaMessage message) {
        tracker.recordInvocation(message.idempotencyKey(), message.payload());
    }
}
