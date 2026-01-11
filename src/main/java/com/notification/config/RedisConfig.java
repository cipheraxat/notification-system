package com.notification.config;

// =====================================================
// RedisConfig.java - Redis Configuration
// =====================================================
//
// Redis is used for:
// 1. Rate limiting (token bucket counters)
// 2. Caching (user info, device info, notification templates)
//
// This configuration sets up the Redis connection and caching.
//

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Duration;

/**
 * Redis configuration.
 *
 * Using Lettuce as the Redis client (Spring Boot default).
 * Lettuce is non-blocking and uses Netty for async operations.
 *
 * @EnableCaching enables Spring's caching annotations (@Cacheable, etc.)
 */
@Configuration
@EnableCaching
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

    /**
     * Create the Cache Manager for Spring Cache abstraction.
     *
     * This enables @Cacheable, @CachePut, @CacheEvict annotations.
     * Used for caching user info, device info, and notification templates.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Create ObjectMapper with JSR310 module for Java 8 date/time support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // Disable writing dates as timestamps, use ISO format instead
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Enable default typing to include type information in JSON
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        
        // Use GenericJackson2JsonRedisSerializer with type information
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);


        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            // Use string serialization for keys
            .serializeKeysWith(
                org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                    .fromSerializer(serializer))
            // Default TTL: 1 hour for cached data
            .entryTtl(Duration.ofHours(1))
            // Don't cache null values
            .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
