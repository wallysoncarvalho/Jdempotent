package com.trendyol.jdempotent.postgres;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Jdempotent PostgreSQL integration.
 * 
 * <p>This class provides configuration options for customizing the behavior of the PostgreSQL
 * idempotent repository implementation. All properties are optional and have sensible defaults.</p>
 * 
 * <h3>Configuration Properties:</h3>
 * <ul>
 *   <li><strong>jdempotent.postgres.tableName</strong> - Database table name for storing idempotent data</li>
 *   <li><strong>jdempotent.postgres.entityManagerBeanName</strong> - Specific EntityManager bean name for multi-database scenarios</li>
 *   <li><strong>jdempotent.cache.persistReqRes</strong> - Whether to persist request/response data as JSON</li>
 * </ul>
 * 
 * <h3>Example Configuration:</h3>
 * <pre>
 * # application.properties
 * jdempotent.postgres.tableName=my_idempotent_table
 * jdempotent.postgres.entityManagerBeanName=myCustomEntityManager
 * jdempotent.cache.persistReqRes=true
 * </pre>
 * 
 * @author Wallyson Soares
 * @since 2.0.0
 */
@Configuration
@ConditionalOnProperty(
        prefix="jdempotent", name = "enable",
        havingValue = "true",
        matchIfMissing = true)
public class JdempotentPostgresProperties {

    /**
     * The name of the PostgreSQL table used to store idempotent request/response data.
     * 
     * <p>This table will be created using the provided SQL script and should contain columns:
     * idempotency_key, request_data, response_data, created_at, and expires_at.</p>
     * 
     * <p><strong>Default:</strong> "jdempotent"</p>
     * <p><strong>Property:</strong> jdempotent.postgres.tableName</p>
     * 
     * @see #getTableName()
     * @see #setTableName(String)
     */
    @Value("${jdempotent.postgres.tableName:jdempotent}")
    private String tableName;

    /**
     * The name of a specific EntityManager bean to use for database operations.
     * 
     * <p>This is useful in multi-database scenarios where you have multiple EntityManager beans
     * and need to specify which one should be used for idempotent operations. If not specified,
     * the default EntityManager bean will be used.</p>
     * 
     * <p><strong>Default:</strong> "" (empty - uses default EntityManager)</p>
     * <p><strong>Property:</strong> jdempotent.postgres.entityManagerBeanName</p>
     * 
     * <h4>Example Usage:</h4>
     * <pre>
     * # For applications with multiple databases
     * jdempotent.postgres.entityManagerBeanName=primaryEntityManager
     * </pre>
     * 
     * @see #getEntityManagerBeanName()
     * @see #setEntityManagerBeanName(String)
     */
    @Value("${jdempotent.postgres.entityManagerBeanName:}")
    private String entityManagerBeanName;

    /**
     * Whether to persist request and response data as JSON in the database.
     * 
     * <p>When enabled, the full request and response objects are serialized to JSON and stored
     * in the request_data and response_data columns. This allows for complete audit trails and
     * debugging capabilities but increases storage requirements.</p>
     * 
     * <p>When disabled, only the idempotency key and expiration information are stored,
     * reducing storage overhead but limiting debugging capabilities.</p>
     * 
     * <p><strong>Default:</strong> true</p>
     * <p><strong>Property:</strong> jdempotent.cache.persistReqRes</p>
     * 
     * <h4>Storage Impact:</h4>
     * <ul>
     *   <li><strong>true:</strong> Full request/response data stored as JSON (higher storage, better debugging)</li>
     *   <li><strong>false:</strong> Only keys and metadata stored (lower storage, limited debugging)</li>
     * </ul>
     * 
     * @see #getPersistReqRes()
     * @see #setPersistReqRes(Boolean)
     */
    @Value("${jdempotent.cache.persistReqRes:true}")
    private Boolean persistReqRes;

    /**
     * Gets the configured table name for storing idempotent data.
     * 
     * @return the table name, defaults to "jdempotent" if not configured
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Sets the table name for storing idempotent data.
     * 
     * @param tableName the table name to use
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Gets the configured EntityManager bean name.
     * 
     * @return the EntityManager bean name, or empty string if default should be used
     */
    public String getEntityManagerBeanName() {
        return entityManagerBeanName;
    }

    /**
     * Sets the EntityManager bean name to use for database operations.
     * 
     * @param entityManagerBeanName the bean name of the EntityManager to use,
     *                              or empty string to use the default EntityManager
     */
    public void setEntityManagerBeanName(String entityManagerBeanName) {
        this.entityManagerBeanName = entityManagerBeanName;
    }

    /**
     * Gets whether request/response data should be persisted as JSON.
     * 
     * @return true if request/response data should be stored, false otherwise
     */
    public Boolean getPersistReqRes() {
        return persistReqRes;
    }

    /**
     * Sets whether request/response data should be persisted as JSON.
     * 
     * @param persistReqRes true to store request/response data as JSON,
     *                      false to store only keys and metadata
     */
    public void setPersistReqRes(Boolean persistReqRes) {
        this.persistReqRes = persistReqRes;
    }
}
