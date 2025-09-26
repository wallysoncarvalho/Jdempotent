# Jdempotent PostgreSQL Starter

This module provides PostgreSQL integration for the Jdempotent library, allowing idempotent operations to be stored and retrieved from a PostgreSQL database.

## Features

- PostgreSQL-based idempotent request/response storage
- Configurable table name (default: `jdempotent`)
- TTL (Time To Live) support for automatic cleanup
- Support for multiple EntityManager beans
- JSON serialization of request/response data
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
    request_data TEXT,
    response_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_jdempotent_expires_at ON jdempotent(expires_at);
```

### 3. Configuration

Configure the PostgreSQL starter in your `application.properties` or `application.yml`:

```properties
# Enable Jdempotent (default: true)
jdempotent.enable=true

# PostgreSQL specific configuration
jdempotent.datasource.postgres.tableName=jdempotent
jdempotent.datasource.postgres.entityManagerBeanName=
jdempotent.datasource.postgres.expirationTimeSeconds=3600

# General configuration
jdempotent.cache.persistReqRes=true
```

## Configuration Properties

| Property | Description | Default Value |
|----------|-------------|---------------|
| `jdempotent.enable` | Enable/disable Jdempotent | `true` |
| `jdempotent.datasource.postgres.tableName` | Database table name | `jdempotent` |
| `jdempotent.datasource.postgres.entityManagerBeanName` | Specific EntityManager bean name (optional) | `` |
| `jdempotent.datasource.postgres.expirationTimeSeconds` | Default TTL in seconds | `3600` |
| `jdempotent.cache.persistReqRes` | Whether to persist request/response data | `true` |

## Multiple EntityManager Support

If your application uses multiple databases and has multiple EntityManager beans, you can specify which one to use:

```properties
jdempotent.datasource.postgres.entityManagerBeanName=myCustomEntityManager
```

If not specified, the default EntityManager bean will be used.

## Usage

Once configured, you can use the `@Jdempotent` annotation on your methods:

```java
@Service
public class MyService {
    
    @Jdempotent
    public String processRequest(String requestId, String data) {
        // Your business logic here
        return "processed: " + data;
    }
}
```

## TTL and Cleanup

The PostgreSQL starter supports automatic expiration of idempotent records through the `expires_at` column. You can:

1. Set a default TTL using `jdempotent.datasource.postgres.expirationTimeSeconds`
2. Use the provided cleanup function to remove expired records:

```sql
SELECT cleanup_expired_jdempotent_records();
```

Consider setting up a scheduled job to run this cleanup function periodically.

## Table Schema

The table structure is designed to store:

- `idempotency_key`: The unique key for the idempotent operation (Primary Key)
- `request_data`: JSON serialized request data (optional, based on `persistReqRes` setting)
- `response_data`: JSON serialized response data (optional, based on `persistReqRes` setting)
- `created_at`: Timestamp when the record was created
- `expires_at`: Timestamp when the record should expire (nullable)

## License

This project is licensed under the MIT License - see the [LICENSE](../LICENSE) file for details.
