package com.notification.model.enums;

// =====================================================
// Priority.java - Notification Priority Levels
// =====================================================
//
// Priority determines HOW URGENTLY a notification is processed.
// 
// Real-world analogy:
// - HIGH = Fire alarm (drop everything, handle NOW)
// - MEDIUM = Regular mail (handle in normal order)
// - LOW = Newsletter (handle when you have time)
//
// In our system:
// - HIGH priority notifications are processed first
// - They might skip rate limiting for critical messages
// - They get fewer retries but faster ones
//

/**
 * Represents the urgency level of a notification.
 * 
 * Priority affects:
 * 1. Processing order - Higher priority processed first
 * 2. Rate limiting - HIGH might bypass rate limits
 * 3. Retry strategy - Different delay patterns
 */
public enum Priority {

    // ==================== Priority Levels ====================
    
    /**
     * HIGH - Urgent notifications that need immediate delivery
     * 
     * Use cases:
     * - OTP codes (time-sensitive, useless if delayed)
     * - Password reset links
     * - Security alerts (login from new device)
     * - Order cancellations
     * - Emergency notifications
     * 
     * Characteristics:
     * - Processed immediately (jump the queue)
     * - May bypass rate limiting
     * - Shorter retry delays (30s instead of 1min)
     * - Fewer retry attempts (critical = send fast or fail fast)
     */
    HIGH(1, "Urgent, process immediately"),
    
    /**
     * MEDIUM - Standard notifications (default)
     * 
     * Use cases:
     * - Order confirmations
     * - Shipping updates
     * - Appointment reminders
     * - Account updates
     * - Most transactional emails
     * 
     * Characteristics:
     * - Processed in normal order
     * - Subject to standard rate limiting
     * - Normal retry delays (1min, 5min, 15min)
     * - Standard retry count (3 attempts)
     */
    MEDIUM(2, "Normal priority, standard processing"),
    
    /**
     * LOW - Non-urgent notifications that can wait
     * 
     * Use cases:
     * - Marketing emails
     * - Weekly digests
     * - Feature announcements
     * - Tips and suggestions
     * - Social notifications (likes, follows)
     * 
     * Characteristics:
     * - Processed after HIGH and MEDIUM
     * - Strictly rate limited
     * - Longer retry delays (5min, 15min, 1hr)
     * - More retry attempts (can wait for transient issues)
     * - May be batched together
     */
    LOW(3, "Can wait, process when resources available");
    
    // ==================== Enum Fields ====================
    
    /**
     * Numeric weight for sorting.
     * Lower number = higher priority.
     * 
     * This allows us to sort notifications:
     *   ORDER BY priority_weight ASC
     * Result: HIGH (1) → MEDIUM (2) → LOW (3)
     */
    private final int weight;
    
    /**
     * Human-readable description.
     */
    private final String description;
    
    // ==================== Constructor ====================
    
    /**
     * Enum constructor.
     * 
     * @param weight      Numeric priority weight (lower = more urgent)
     * @param description Human-readable description
     */
    Priority(int weight, String description) {
        this.weight = weight;
        this.description = description;
    }
    
    // ==================== Getter Methods ====================
    
    /**
     * Get the numeric weight of this priority.
     * 
     * Usage: Priority.HIGH.getWeight() → 1
     * 
     * @return The weight (1 = highest, 3 = lowest)
     */
    public int getWeight() {
        return weight;
    }
    
    /**
     * Get the description of this priority.
     * 
     * @return The human-readable description
     */
    public String getDescription() {
        return description;
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Check if this priority is higher than another.
     * 
     * Usage:
     *   Priority.HIGH.isHigherThan(Priority.MEDIUM) → true
     *   Priority.LOW.isHigherThan(Priority.MEDIUM) → false
     * 
     * @param other The priority to compare against
     * @return true if this priority is more urgent
     */
    public boolean isHigherThan(Priority other) {
        // Lower weight = higher priority
        return this.weight < other.weight;
    }
    
    /**
     * Convert a string to Priority enum.
     * 
     * @param value The string value (case-insensitive)
     * @return The corresponding Priority
     * @throws IllegalArgumentException If value doesn't match
     */
    public static Priority fromValue(String value) {
        for (Priority priority : Priority.values()) {
            if (priority.name().equalsIgnoreCase(value)) {
                return priority;
            }
        }
        throw new IllegalArgumentException(
            "Unknown priority: " + value + 
            ". Valid values are: HIGH, MEDIUM, LOW"
        );
    }
    
    /**
     * Get the default priority.
     * 
     * Used when the caller doesn't specify a priority.
     * 
     * @return MEDIUM (the default priority)
     */
    public static Priority getDefault() {
        return MEDIUM;
    }
}
