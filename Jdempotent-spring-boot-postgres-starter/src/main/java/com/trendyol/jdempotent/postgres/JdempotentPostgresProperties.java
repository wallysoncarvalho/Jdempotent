package com.trendyol.jdempotent.postgres;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Jdempotent PostgreSQL integration
 */
@Configuration
@ConditionalOnProperty(
        prefix="jdempotent", name = "enable",
        havingValue = "true",
        matchIfMissing = true)
public class JdempotentPostgresProperties {

    @Value("${jdempotent.postgres.tableName:jdempotent}")
    private String tableName;

    @Value("${jdempotent.postgres.entityManagerBeanName:}")
    private String entityManagerBeanName;

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

    public Boolean getPersistReqRes() {
        return persistReqRes;
    }

    public void setPersistReqRes(Boolean persistReqRes) {
        this.persistReqRes = persistReqRes;
    }
}
