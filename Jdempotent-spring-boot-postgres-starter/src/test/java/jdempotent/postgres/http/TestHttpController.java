package jdempotent.postgres.http;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @PostMapping("/process-error")
    @JdempotentResource(cachePrefix = "http-error", ttl = 5, ttlTimeUnit = TimeUnit.MINUTES)
    public ResponsePayload processError(
            @RequestBody @JdempotentRequestPayload RequestPayload payload) {
        tracker.recordInvocation(payload.idempotencyKey());
        throw new RuntimeException("Error processing request");
    }

    @PostMapping("/process-with-key")
    @JdempotentResource(cachePrefix = "http-with-key", ttl = 5, ttlTimeUnit = TimeUnit.MINUTES)
    public ResponsePayload processWithKey(
            @JdempotentId @RequestHeader("x-idempotency-key") String idempotencyKey,
            @RequestBody @JdempotentRequestPayload RequestPayload payload) {
        tracker.recordInvocation(idempotencyKey);
        return new ResponsePayload(idempotencyKey, "processed-" + payload.data());
    }

    public record RequestPayload(@JdempotentId String idempotencyKey, String data) implements Serializable {}

    public record ResponsePayload(String idempotencyKey, String result) implements Serializable {}
}

@SpringBootApplication
class TestHttpApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestHttpApplication.class, args);
    }
}
