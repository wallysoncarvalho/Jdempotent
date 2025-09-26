package aspect.core;

import com.trendyol.jdempotent.core.annotation.JdempotentId;

public class TestPayloadWithKey {
    @JdempotentId
    private String idempotencyKey;

    private String name;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
