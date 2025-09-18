package com.trendyol.jdempotent.redis;


import com.trendyol.jdempotent.core.model.IdempotentResponseWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 *
 */
@Configuration
@ConditionalOnProperty(
        prefix="jdempotent", name = "enable",
        havingValue = "true",
        matchIfMissing = true)
public class RedisSentinelConfiguration {

    private final RedisConfigProperties redisProperties;

    public RedisSentinelConfiguration(RedisConfigProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        org.springframework.data.redis.connection.RedisSentinelConfiguration sentinelConfiguration = new org.springframework.data.redis.connection.RedisSentinelConfiguration().master(redisProperties.getSentinelMasterName());
        
        redisProperties.getSentinelHostList().forEach(host -> {
                System.out.println("hhost: " + host);
                sentinelConfiguration.sentinel(host, redisProperties.getSentinelPort());
        });

        sentinelConfiguration.setPassword(redisProperties.getPassword());
        sentinelConfiguration.setDatabase(redisProperties.getDatabase());

        return new LettuceConnectionFactory(
                sentinelConfiguration,
                LettuceClientConfiguration.defaultConfiguration()
                );
    }

    @Bean(name = "trandyolRedisTemplate")
    public RedisTemplate<String, IdempotentResponseWrapper> redisTemplate() {
        RedisTemplate<String, IdempotentResponseWrapper> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(lettuceConnectionFactory());
        redisTemplate.afterPropertiesSet();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setDefaultSerializer(new GenericJackson2JsonRedisSerializer());
        return redisTemplate;
    }
}
