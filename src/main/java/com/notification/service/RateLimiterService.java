package com.notification.service;

// =====================================================
// RateLimiterService.java - Rate Limiting Logic
// =====================================================
//
// Rate limiting controls HOW MANY notifications a user can
// receive per time period. This prevents:
// - Spam (accidental or intentional)
// - Cost overruns (SMS is expensive!)
// - User annoyance (too many notifications)
//
// Algorithm: Token Bucket (simple version)
// - Each user/channel combo has a "bucket" of tokens
// - Sending a notification consumes 1 token
// - Tokens refill over time (1 per X seconds)
// - When bucket is empty, rate limit is exceeded
//
// We use Redis to store rate limit counters because:
// - Fast (in-memory)
// - Supports TTL (auto-expire keys)
// - Shared across multiple application instances
//

import com.notification.exception.RateLimitExceededException;
import com.notification.model.enums.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for rate limiting notifications.
 * 
 * Uses Redis to track how many notifications each user
 * has received per channel in the current hour.
 * 
 * @Service marks this as a Spring service bean.
 * It will be automatically created and injectable.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    
    // ==================== Dependencies ====================
    
    /**
     * Redis template for string operations.
     * 
     * StringRedisTemplate is a specialized RedisTemplate
     * for working with String keys and values.
     */
    private final StringRedisTemplate redisTemplate;
    
    // ==================== Configuration ====================
    // These values come from application.yml
    
    @Value("${notification.rate-limit.email:10}")
    private int emailLimit;
    
    @Value("${notification.rate-limit.sms:5}")
    private int smsLimit;
    
    @Value("${notification.rate-limit.push:20}")
    private int pushLimit;
    
    @Value("${notification.rate-limit.in-app:100}")
    private int inAppLimit;
    
    // Time window for rate limiting (1 hour = 3600 seconds)
    private static final long WINDOW_SECONDS = 3600;
    
    // ==================== Constructor ====================
    
    /**
     * Constructor injection of dependencies.
     * 
     * Spring will automatically pass in the StringRedisTemplate
     * when creating this service.
     */
    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    // ==================== Public Methods ====================
    
    /**
     * Check if a notification can be sent (rate limit not exceeded).
     * 
     * If the limit is exceeded, throws RateLimitExceededException.
     * If under limit, increments the counter and returns true.
     * 
     * @param userId The user receiving the notification
     * @param channel The notification channel
     * @return true if notification can be sent
     * @throws RateLimitExceededException if limit exceeded
     */
    public boolean checkAndIncrement(UUID userId, ChannelType channel) {
        String key = buildKey(userId, channel);
        int limit = getLimitForChannel(channel);
        
        // Get current count
        String countStr = redisTemplate.opsForValue().get(key);
        int currentCount = countStr != null ? Integer.parseInt(countStr) : 0;
        
        log.debug("Rate limit check for user {} channel {}: {}/{}", 
            userId, channel, currentCount, limit);
        
        if (currentCount >= limit) {
            // Calculate when the limit will reset
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfter = ttl != null && ttl > 0 ? ttl : WINDOW_SECONDS;
            
            log.warn("Rate limit exceeded for user {} channel {}. Retry after {} seconds.",
                userId, channel, retryAfter);
            
            throw new RateLimitExceededException(channel, limit, retryAfter);
        }
        
        // Increment the counter
        // INCR is atomic - safe for concurrent access
        Long newCount = redisTemplate.opsForValue().increment(key);
        
        // Set expiry on first increment
        if (newCount != null && newCount == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS));
        }
        
        log.debug("Rate limit incremented for user {} channel {}: {}/{}", 
            userId, channel, newCount, limit);
        
        return true;
    }
    
    /**
     * Get remaining quota for a user/channel.
     * 
     * Useful for showing users: "You have 8 emails remaining"
     * 
     * @param userId The user
     * @param channel The channel
     * @return Number of notifications still allowed
     */
    public int getRemainingQuota(UUID userId, ChannelType channel) {
        String key = buildKey(userId, channel);
        int limit = getLimitForChannel(channel);
        
        String countStr = redisTemplate.opsForValue().get(key);
        int currentCount = countStr != null ? Integer.parseInt(countStr) : 0;
        
        return Math.max(0, limit - currentCount);
    }
    
    /**
     * Check if rate limit would be exceeded (without incrementing).
     * 
     * Used for pre-flight checks before queuing notifications.
     */
    public boolean isRateLimited(UUID userId, ChannelType channel) {
        String key = buildKey(userId, channel);
        int limit = getLimitForChannel(channel);
        
        String countStr = redisTemplate.opsForValue().get(key);
        int currentCount = countStr != null ? Integer.parseInt(countStr) : 0;
        
        return currentCount >= limit;
    }
    
    /**
     * Reset rate limit for a user/channel (admin function).
     * 
     * Use sparingly! This bypasses rate limiting.
     */
    public void resetLimit(UUID userId, ChannelType channel) {
        String key = buildKey(userId, channel);
        redisTemplate.delete(key);
        log.info("Rate limit reset for user {} channel {}", userId, channel);
    }
    
    // ==================== Private Helper Methods ====================
    
    /**
     * Build the Redis key for rate limiting.
     * 
     * Format: "ratelimit:{userId}:{channel}"
     * Example: "ratelimit:123-abc:EMAIL"
     * 
     * Using a consistent key format makes it easy to:
     * - Query all rate limits for a user
     * - Clean up old data
     * - Debug issues
     */
    private String buildKey(UUID userId, ChannelType channel) {
        return String.format("ratelimit:%s:%s", userId.toString(), channel.name());
    }
    
    /**
     * Get the rate limit for a specific channel.
     */
    private int getLimitForChannel(ChannelType channel) {
        return switch (channel) {
            case EMAIL -> emailLimit;
            case SMS -> smsLimit;
            case PUSH -> pushLimit;
            case IN_APP -> inAppLimit;
        };
    }
}
