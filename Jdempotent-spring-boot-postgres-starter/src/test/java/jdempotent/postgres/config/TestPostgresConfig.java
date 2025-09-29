package jdempotent.postgres.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.trendyol.jdempotent.postgres.JdempotentPostgresProperties;
import com.trendyol.jdempotent.postgres.PostgresIdempotentRepository;

import jakarta.persistence.EntityManagerFactory;

@SpringBootConfiguration
@EnableAutoConfiguration
public class TestPostgresConfig {

    @Bean
    @Primary
    public PostgresIdempotentRepository postgresIdempotentRepository(
            EntityManagerFactory entityManagerFactory,
            JdempotentPostgresProperties properties) {
        return new PostgresIdempotentRepository(entityManagerFactory, properties);
    }
    
    @Bean
    @Primary
    public JdempotentPostgresProperties jdempotentPostgresProperties() {
        return new JdempotentPostgresProperties();
    }
}
