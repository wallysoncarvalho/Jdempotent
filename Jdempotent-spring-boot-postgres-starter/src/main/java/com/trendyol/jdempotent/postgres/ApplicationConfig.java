package com.trendyol.jdempotent.postgres;

import com.trendyol.jdempotent.core.aspect.IdempotentAspect;
import com.trendyol.jdempotent.core.callback.ErrorConditionalCallback;
import com.trendyol.jdempotent.core.generator.KeyGenerator;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityManager;

@Configuration
@ConditionalOnProperty(prefix = "jdempotent", name = "enable", havingValue = "true", matchIfMissing = true)
public class ApplicationConfig {

    private final JdempotentPostgresProperties postgresProperties;
    private final ApplicationContext applicationContext;

    public ApplicationConfig(JdempotentPostgresProperties postgresProperties, ApplicationContext applicationContext) {
        this.postgresProperties = postgresProperties;
        this.applicationContext = applicationContext;
    }

    /**
     * Creates IdempotentAspect with ErrorConditionalCallback when available
     */
    @Bean
    @ConditionalOnProperty(prefix = "jdempotent", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(ErrorConditionalCallback.class)
    @ConditionalOnBean(ErrorConditionalCallback.class)
    public IdempotentAspect getIdempotentAspect(ErrorConditionalCallback errorConditionalCallback) {
        EntityManager entityManager = resolveEntityManager();
        return new IdempotentAspect(new PostgresIdempotentRepository(entityManager, postgresProperties, postgresProperties.getTableName()),
                errorConditionalCallback);
    }

    /**
     * Creates default IdempotentAspect when no ErrorConditionalCallback is available
     */
    @Bean
    @ConditionalOnMissingBean({ IdempotentAspect.class, KeyGenerator.class })
    public IdempotentAspect defaultGetIdempotentAspect() {
        EntityManager entityManager = resolveEntityManager();
        return new IdempotentAspect(new PostgresIdempotentRepository(entityManager, postgresProperties, postgresProperties.getTableName()));
    }

    /**
     * Resolves the EntityManager bean to use.
     * If entityManagerBeanName is specified in properties, uses that specific bean.
     * Otherwise, uses the default EntityManager bean.
     */
    private EntityManager resolveEntityManager() {
        String beanName = postgresProperties.getEntityManagerBeanName();
        
        if (StringUtils.hasText(beanName)) {
            // Use the specific EntityManager bean name provided in configuration
            return applicationContext.getBean(beanName, EntityManager.class);
        } else {
            // Use the default EntityManager bean
            return applicationContext.getBean(EntityManager.class);
        }
    }
}
