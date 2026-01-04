package com.notification.exception;

// =====================================================
// RateLimitExceededException.java - Rate Limit Exception
// =====================================================
//
// Thrown when a user has exceeded their rate limit for a channel.
// 
// Rate limiting prevents abuse and protects our system:
// - Email: 10 per hour
// - SMS: 5 per hour (expensive!)
// - Push: 20 per hour
// - In-app: 100 per hour
//

import com.notification.model.enums.ChannelType;

/**
 * Exception thrown when rate limit is exceeded.
 * 
 * This will be converted to a 429 Too Many Requests response.
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * The channel that exceeded the limit.
     */
    private final ChannelType channel;
    
    /**
     * The limit that was exceeded.
     */
    private final int limit;
    
    /**
     * How many seconds until the limit resets.
     */
    private final long retryAfterSeconds;
    
    /**
     * Create a new RateLimitExceededException.
     * 
     * @param channel The channel that hit the limit
     * @param limit The maximum allowed per time window
     * @param retryAfterSeconds Seconds until the limit resets
     */
    public RateLimitExceededException(ChannelType channel, int limit, long retryAfterSeconds) {
        super(String.format(
            "Rate limit exceeded for %s. Limit: %d per hour. Retry after %d seconds.",
            channel.name(), limit, retryAfterSeconds
        ));
        this.channel = channel;
        this.limit = limit;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    // Getters
    public ChannelType getChannel() { return channel; }
    public int getLimit() { return limit; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
