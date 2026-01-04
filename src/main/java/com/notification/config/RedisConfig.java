package com.notification.config;

// =====================================================
// RedisConfig.java - Redis Configuration
// =====================================================
//
// Redis is used for rate limiting in our system.
// This configuration sets up the Redis connection.
//

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration.
 * 
 * Using Lettuce as the Redis client (Spring Boot default).
 * Lettuce is non-blocking and uses Netty for async operations.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;
    
    /**
     * Create the Redis connection factory.
     * 
     * This factory creates connections to Redis.
     * Lettuce manages a connection pool internally.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        
        return new LettuceConnectionFactory(config);
    }
    
    /**
     * Create the StringRedisTemplate.
     * 
     * This template is optimized for String keys and values,
     * which is what we use for rate limiting.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
