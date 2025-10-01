package com.trendyol.jdempotent.postgres;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.trendyol.jdempotent.core.aspect.IdempotentAspect;
import com.trendyol.jdempotent.core.callback.ErrorConditionalCallback;
import com.trendyol.jdempotent.core.generator.KeyGenerator;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@ConditionalOnProperty(prefix = "jdempotent", name = "enable", havingValue = "true", matchIfMissing = true)
public class ApplicationConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

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
        EntityManagerFactory entityManagerFactory = resolveEntityManagerFactory();
        return new IdempotentAspect(new PostgresIdempotentRepository(entityManagerFactory, postgresProperties),
                errorConditionalCallback);
    }

    /**
     * Creates default IdempotentAspect when no ErrorConditionalCallback is available
     */
    @Bean
    @ConditionalOnMissingBean({ IdempotentAspect.class, KeyGenerator.class })
    public IdempotentAspect defaultGetIdempotentAspect() {
        EntityManagerFactory entityManagerFactory = resolveEntityManagerFactory();
        return new IdempotentAspect(new PostgresIdempotentRepository(entityManagerFactory, postgresProperties));
    }

    /**
     * Resolves the EntityManagerFactory bean to use.
     * If entityManagerBeanName is specified in properties, tries to find the corresponding EntityManagerFactory.
     * Otherwise, uses the default EntityManagerFactory bean.
     * 
     * Note: Using EntityManagerFactory instead of EntityManager for thread safety.
     * Each repository operation will create its own EntityManager instance.
     */
    private EntityManagerFactory resolveEntityManagerFactory() {
        String beanName = postgresProperties.getEntityManagerBeanName();
        
        if (!StringUtils.hasText(beanName)) {
            return applicationContext.getBean(EntityManagerFactory.class);
        }
        
        try {
            return applicationContext.getBean(beanName, EntityManagerFactory.class);
        } catch (Exception e) {
            // Fallback: try to get the EntityManagerFactory from the EntityManager bean
            logger.error("Could not find EntityManagerFactory with name '{}'.", beanName);
            throw e;
        }        
    }
}
