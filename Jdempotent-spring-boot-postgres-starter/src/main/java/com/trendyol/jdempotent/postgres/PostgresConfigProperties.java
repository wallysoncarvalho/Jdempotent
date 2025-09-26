package com.trendyol.jdempotent.postgres;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for PostgreSQL idempotent repository
 */
@Configuration
@ConditionalOnProperty(
        prefix="jdempotent", name = "enable",
        havingValue = "true",
        matchIfMissing = true)
public class PostgresConfigProperties {

    @Value("${jdempotent.datasource.postgres.tableName:jdempotent}")
    private String tableName;

    @Value("${jdempotent.datasource.postgres.entityManagerBeanName:}")
    private String entityManagerBeanName;

    @Value("${jdempotent.datasource.postgres.expirationTimeSeconds:3600}")
    private Long expirationTimeSeconds;

    @Value("${jdempotent.cache.persistReqRes:true}")
    private Boolean persistReqRes;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getEntityManagerBeanName() {
        return entityManagerBeanName;
    }

    public void setEntityManagerBeanName(String entityManagerBeanName) {
        this.entityManagerBeanName = entityManagerBeanName;
    }

    public Long getExpirationTimeSeconds() {
        return expirationTimeSeconds;
    }

    public void setExpirationTimeSeconds(Long expirationTimeSeconds) {
        this.expirationTimeSeconds = expirationTimeSeconds;
    }

    public Boolean getPersistReqRes() {
        return persistReqRes;
    }

    public void setPersistReqRes(Boolean persistReqRes) {
        this.persistReqRes = persistReqRes;
    }
}
