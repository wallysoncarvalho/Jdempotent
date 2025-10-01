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

### Batch Cleanup Function

The provided cleanup function removes expired records in configurable batches and supports concurrent execution:

```sql
-- Delete up to 1000 expired records (default batch size)
SELECT cleanup_expired_jdempotent_records();

-- Delete up to 100 expired records
SELECT cleanup_expired_jdempotent_records(100);

-- Delete up to 5000 expired records
SELECT cleanup_expired_jdempotent_records(5000);
```

### Concurrent-Safe Design

The cleanup function uses `SELECT FOR UPDATE SKIP LOCKED` to enable multiple cleanup processes to run concurrently without blocking each other:

- **Multiple processes can run simultaneously**: Each process will work on different sets of expired records
- **No blocking**: Processes skip records that are already being processed by other transactions
- **Batch processing**: Limits the number of records processed per call to prevent long-running operations

## Scheduled Cleanup

The PostgreSQL starter includes built-in support for scheduled cleanup of expired records. This feature automatically removes expired idempotent data at configurable intervals.

### Enabling Scheduled Cleanup

To enable scheduled cleanup, add the following to your `application.properties`:

```properties
# Enable scheduled cleanup (disabled by default)
jdempotent.postgres.scheduler.enabled=true

# Configure batch size (default: 1000)
jdempotent.postgres.scheduler.batchSize=1000
```

### Scheduling Options

You can configure the cleanup schedule using one of three approaches:

#### 1. Fixed Delay Scheduling
Ensures a specific delay between cleanup executions:

```properties
# Run cleanup every 5 minutes after previous execution completes
jdempotent.postgres.scheduler.fixedDelay=300000
jdempotent.postgres.scheduler.initialDelay=60000
```

#### 2. Fixed Rate Scheduling
Schedules cleanup at regular intervals:

```properties
# Run cleanup every hour
jdempotent.postgres.scheduler.fixedRate=3600000
jdempotent.postgres.scheduler.initialDelay=60000
```

#### 3. Cron Expression Scheduling
Provides the most flexible scheduling using cron expressions:

```properties
# Run every day at 2:00 AM
jdempotent.postgres.scheduler.cron=0 0 2 * * ?
jdempotent.postgres.scheduler.zone=UTC

# Run every 30 minutes
jdempotent.postgres.scheduler.cron=0 */30 * * * ?

# Run every Sunday at midnight
jdempotent.postgres.scheduler.cron=0 0 0 * * SUN
```

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `jdempotent.postgres.scheduler.enabled` | `false` | Enable/disable scheduled cleanup |
| `jdempotent.postgres.scheduler.batchSize` | `1000` | Number of records to delete per batch |
| `jdempotent.postgres.scheduler.fixedDelay` | - | Delay between executions (milliseconds) |
| `jdempotent.postgres.scheduler.fixedRate` | - | Interval between executions (milliseconds) |
| `jdempotent.postgres.scheduler.initialDelay` | `60000` | Initial delay before first execution (milliseconds) |
| `jdempotent.postgres.scheduler.cron` | - | Cron expression for scheduling |
| `jdempotent.postgres.scheduler.zone` | `UTC` | Time zone for cron expression |

### Manual Cleanup

You can also trigger cleanup operations manually by injecting the cleanup service:

```java
@Autowired
private JdempotentPostgresCleanupService cleanupService;

public void performManualCleanup() {
    int deletedRecords = cleanupService.manualCleanup();
    logger.info("Manually deleted {} expired records", deletedRecords);
}
```

### Monitoring and Logging

The cleanup service provides comprehensive logging:

- **INFO level**: Successful cleanup operations with record counts
- **DEBUG level**: Detailed execution information
- **ERROR level**: Cleanup failures with full stack traces

Enable debug logging for detailed cleanup information:

```properties
logging.level.com.trendyol.jdempotent.postgres.JdempotentPostgresCleanupService=DEBUG
```

### Best Practices

1. **Batch Size**: Use smaller batches (100-500) for high-concurrency environments, larger batches (1000-5000) for better efficiency in low-concurrency scenarios
2. **Scheduling**: Use off-peak hours for cleanup operations to minimize impact on application performance
3. **Monitoring**: Monitor cleanup execution times and database performance after enabling scheduled cleanup
4. **Testing**: Always test cleanup configuration in non-production environments first
- **Oldest first**: Processes expired records in order of expiration time

### Complete Cleanup Example

For complete cleanup in batches (useful for scheduled jobs):

```sql
DO $$
DECLARE
    deleted INTEGER;
    total_deleted INTEGER := 0;
BEGIN
    LOOP
        SELECT cleanup_expired_jdempotent_records(1000) INTO deleted;
        total_deleted := total_deleted + deleted;
        EXIT WHEN deleted = 0;
        RAISE NOTICE 'Batch completed: % records deleted (total: %)', deleted, total_deleted;
    END LOOP;
    RAISE NOTICE 'Cleanup completed: % total records deleted', total_deleted;
END $$;
```

### Scheduling Cleanup

Consider setting up a scheduled job to run this cleanup function periodically:

- **pg_cron extension**: Schedule directly in PostgreSQL
- **Application scheduler**: Use Spring's `@Scheduled` annotation
- **External cron job**: Call the function from external scripts

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
