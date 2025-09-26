package aspect.core;

import com.trendyol.jdempotent.core.annotation.JdempotentIgnore;
import com.trendyol.jdempotent.core.annotation.JdempotentProperty;

public class IdempotentTestPayload {
    private String name;
    @JdempotentIgnore
    private Long age;

    @JdempotentProperty("transactionId")
    private Long eventId;

    public IdempotentTestPayload() {
    }

    public IdempotentTestPayload(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public void setAge(Long age) {
        this.age = age;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        IdempotentTestPayload that = (IdempotentTestPayload) obj;
        
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (eventId != null ? !eventId.equals(that.eventId) : that.eventId != null) return false;
        // Note: age is ignored in comparison as it has @JdempotentIgnore annotation
        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (eventId != null ? eventId.hashCode() : 0);
        // Note: age is ignored in hash calculation as it has @JdempotentIgnore annotation
        return result;
    }
}