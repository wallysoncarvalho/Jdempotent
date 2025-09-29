# Jdempotent PostgreSQL Starter

This module provides PostgreSQL integration for the Jdempotent library, allowing idempotent operations to be stored and retrieved from a PostgreSQL database.

## Features

- PostgreSQL-based idempotent request/response storage
- Configurable table name (default: `jdempotent`)
- TTL (Time To Live) support via `@JdempotentResource` annotation
- Cache prefix support via `@JdempotentResource` annotations
- Support for multiple `EntityManager` beans
- Byte array serialization of request/response data for efficiency
- Spring Boot auto-configuration

## Getting Started

### 1. Add Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.trendyol</groupId>
    <artifactId>Jdempotent-spring-boot-postgres-starter</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 2. Database Setup

Execute the SQL script provided in `src/main/resources/jdempotent-table.sql` to create the required table structure:

```sql
CREATE TABLE IF NOT EXISTS jdempotent (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    cache_prefix VARCHAR(255),
    request_data BYTEA,
    response_data BYTEA,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_jdempotent_expires_at ON jdempotent(expires_at);

CREATE OR REPLACE FUNCTION cleanup_expired_jdempotent_records()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM jdempotent WHERE expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;
```

### 3. Configuration

Configure the PostgreSQL starter in your `application.properties` or `application.yml`:

```properties
# Enable Jdempotent (default: true)
jdempotent.enable=true

# PostgreSQL specific configuration
jdempotent.postgres.tableName=jdempotent
jdempotent.postgres.entityManagerBeanName=

# General configuration
jdempotent.cache.persistReqRes=true
```

## Configuration Properties

| Property | Description | Default Value |
|----------|-------------|---------------|
| `jdempotent.enable` | Enable/disable Jdempotent | `true` |
| `jdempotent.postgres.tableName` | Database table name | `jdempotent` |
| `jdempotent.postgres.entityManagerBeanName` | Specific `EntityManager` bean name (optional) | `` |
| `jdempotent.cache.persistReqRes` | Whether to persist request/response data as byte arrays ⚠️ **Requires Serializable classes** | `true` |

**Note**: TTL and cache prefix are configured per method via the `@JdempotentResource` annotation's `ttl`, `ttlTimeUnit`, and `cachePrefix` properties, not through configuration files.

## Multiple EntityManager Support

If your application uses multiple databases and has multiple `EntityManager` beans, you can specify which one to use:

```properties
jdempotent.postgres.entityManagerBeanName=myCustomEntityManager
```

If not specified, the default `EntityManager` bean will be used.

## Usage

Once configured, you can use the `@JdempotentResource` annotation on your methods with TTL and cache prefix configuration:

```java
@Service
public class MyService {
    
    @JdempotentResource(ttl = 30, ttlTimeUnit = TimeUnit.MINUTES, cachePrefix = "process")
    public ResponsePayload processRequest(@JdempotentRequestPayload RequestPayload payload) {
        // Your business logic here
        return new ResponsePayload("processed: " + payload.data());
    }
}

// Request and response classes must implement Serializable when persistReqRes=true
public record RequestPayload(@JdempotentId String requestId, String data) implements java.io.Serializable {}

public record ResponsePayload(String result) implements java.io.Serializable {}
```

> **⚠️ Important**: When using the default setting `jdempotent.cache.persistReqRes=true`, all request payload and response classes must implement `java.io.Serializable`. See the [Serializable Requirement](#serializable-requirement) section for more details.

## TTL and Cleanup

The PostgreSQL starter supports automatic expiration of idempotent records through the `expires_at` column. TTL is configured per method using the `@JdempotentResource` annotation:

```java
@JdempotentResource(ttl = 1, ttlTimeUnit = TimeUnit.HOURS)
public String myMethod() {
    // This method's idempotent data will expire after 1 hour
}
```

You can use the provided cleanup function to remove expired records:

```sql
SELECT cleanup_expired_jdempotent_records();
```

Consider setting up a scheduled job to run this cleanup function periodically.

## Table Schema

The table structure is designed to store:

- `idempotency_key`: The unique key for the idempotent operation (Primary Key)
- `cache_prefix`: An optional prefix to namespace cache entries
- `request_data`: Byte array serialized request data (BYTEA, optional, based on `persistReqRes` setting)
- `response_data`: Byte array serialized response data (BYTEA, optional, based on `persistReqRes` setting)
- `created_at`: Timestamp when the record was created
- `expires_at`: Timestamp when the record should expire (nullable)

### Why Byte Arrays Instead of JSON?

The PostgreSQL starter uses byte array serialization instead of JSON for several advantages:
- **Efficiency**: More compact storage and faster serialization/deserialization
- **No Encoding Issues**: Avoids character encoding problems
- **Type Safety**: Preserves exact object types during serialization
- **Performance**: Better database performance with `BYTEA` columns

### Serializable Requirement

**Important**: When `jdempotent.cache.persistReqRes=true` (default), all request payload and response classes must implement `java.io.Serializable`. This is required for the byte array serialization to work properly.

```java
// ✅ Correct - implements Serializable
public record RequestPayload(String id, String data) implements java.io.Serializable {}

public record ResponsePayload(String result) implements java.io.Serializable {}

// ❌ Incorrect - will cause NotSerializableException
public record RequestPayload(String id, String data) {}
```

If you cannot make your classes implement `Serializable`, you have two options:
1. **Disable persistence**: Set `jdempotent.cache.persistReqRes=false` (only idempotency keys will be stored)
2. **Use simple types**: Use primitive types or built-in Serializable classes like `String`, `Integer`, etc.

## License

This project is licensed under the MIT License - see the [LICENSE](../LICENSE) file for details.

