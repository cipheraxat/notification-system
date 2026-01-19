package com.notification.service;

// =====================================================
// DeduplicationService.java - Event Deduplication Logic
// =====================================================
//
// This service prevents duplicate notifications by tracking event IDs.
//
// How it works:
// 1. When a notification request arrives with an eventId
// 2. We check if we've seen this eventId recently (in Redis)
// 3. If seen before → discard (return true = duplicate)
// 4. If not seen → mark as seen and proceed (return false = not duplicate)
//
// Redis key format: "event:{eventId}"
// TTL: 24 hours (configurable) to prevent infinite growth
//
// Why Redis?
// - Fast lookups (O(1))
// - TTL support (auto-expiration)
// - Shared across multiple instances
// - Already used for rate limiting
//

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for deduplicating notification events.
 *
 * Uses Redis to track event IDs that have been processed recently.
 * Prevents duplicate notifications from being sent.
 */
@Service
public class DeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);

    private static final String EVENT_KEY_PREFIX = "event:";

    private final StringRedisTemplate redisTemplate;

    // TTL for event IDs (24 hours = 86400 seconds)
    @Value("${notification.dedupe.ttl-seconds:86400}")
    private long eventTtlSeconds;

    public DeduplicationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if an event has been seen before (is duplicate).
     *
     * @param eventId The unique event identifier
     * @return true if this event has been processed before (duplicate), false if new
     */
    public boolean isDuplicate(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            // No event ID provided, cannot dedupe
            return false;
        }

        String key = EVENT_KEY_PREFIX + eventId;

        // Check if key exists in Redis
        Boolean exists = redisTemplate.hasKey(key);

        if (Boolean.TRUE.equals(exists)) {
            log.info("Duplicate event detected: {}", eventId);
            return true;
        }

        // Mark as seen and set TTL
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(eventTtlSeconds));
        log.debug("Marked event as seen: {}", eventId);

        return false;
    }

    /**
     * Manually mark an event as processed (useful for testing).
     *
     * @param eventId The event ID to mark as seen
     */
    public void markAsSeen(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            String key = EVENT_KEY_PREFIX + eventId;
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(eventTtlSeconds));
            log.debug("Manually marked event as seen: {}", eventId);
        }
    }

    /**
     * Check if an event exists without marking it as seen.
     *
     * @param eventId The event ID to check
     * @return true if the event exists, false otherwise
     */
    public boolean exists(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        String key = EVENT_KEY_PREFIX + eventId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}