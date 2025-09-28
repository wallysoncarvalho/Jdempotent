package jdempotent.postgres.http;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.trendyol.jdempotent.core.annotation.JdempotentId;
import com.trendyol.jdempotent.core.annotation.JdempotentRequestPayload;
import com.trendyol.jdempotent.core.annotation.JdempotentResource;

@RestController
public class TestHttpController {

    private final HttpProcessingTracker tracker;

    public TestHttpController(HttpProcessingTracker tracker) {
        this.tracker = tracker;
    }

    @PostMapping("/process")
    @JdempotentResource(cachePrefix = "http", ttl = 5, ttlTimeUnit = TimeUnit.MINUTES)
    public ResponsePayload process(@RequestBody @JdempotentRequestPayload RequestPayload payload) {
        tracker.recordInvocation(payload.idempotencyKey());
        return new ResponsePayload(payload.idempotencyKey(), "processed-" + payload.data());
    }

    public record RequestPayload(@JdempotentId String idempotencyKey, String data) implements Serializable{}

    public record ResponsePayload(String idempotencyKey, String result) implements Serializable{}
}
