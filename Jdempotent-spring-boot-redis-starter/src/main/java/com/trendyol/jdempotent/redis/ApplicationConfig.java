package com.trendyol.jdempotent.redis;

import com.trendyol.jdempotent.core.aspect.IdempotentAspect;
import com.trendyol.jdempotent.core.callback.ErrorConditionalCallback;
import com.trendyol.jdempotent.core.generator.KeyGenerator;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 *
 */
@Configuration
@ConditionalOnProperty(prefix = "jdempotent", name = "enable", havingValue = "true", matchIfMissing = true)
public class ApplicationConfig {

    private final RedisConfigProperties redisProperties;

    public ApplicationConfig(RedisConfigProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    @Bean
    @ConditionalOnProperty(prefix = "jdempotent", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(ErrorConditionalCallback.class)
    @ConditionalOnBean(ErrorConditionalCallback.class)
    public IdempotentAspect getIdempotentAspect(@Qualifier("trandyolRedisTemplate") RedisTemplate redisTemplate,
            ErrorConditionalCallback errorConditionalCallback) {
        return new IdempotentAspect(new RedisIdempotentRepository(redisTemplate, redisProperties),
                errorConditionalCallback);
    }

    @Bean
    @ConditionalOnMissingBean({ IdempotentAspect.class, KeyGenerator.class })
    public IdempotentAspect defaultGetIdempotentAspect(
            @Qualifier("trandyolRedisTemplate") RedisTemplate redisTemplate) {
        return new IdempotentAspect(new RedisIdempotentRepository(redisTemplate, redisProperties));
    }

    @Bean
    @ConditionalOnBean(KeyGenerator.class)
    @ConditionalOnMissingBean({ IdempotentAspect.class })
    public IdempotentAspect idempotentAspectWithKeyGenerator(
            @Qualifier("trandyolRedisTemplate") RedisTemplate redisTemplate,
            KeyGenerator keyGenerator) {

        var repository = new RedisIdempotentRepository(redisTemplate, redisProperties);

        return new IdempotentAspect(repository, keyGenerator);
    }
}
