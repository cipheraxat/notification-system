package com.notification.model.enums;

// =====================================================
// NotificationStatus.java - Notification Lifecycle States
// =====================================================
//
// This enum tracks the lifecycle of a notification.
// Think of it like a package delivery status:
//
//   Order Placed → Shipped → In Transit → Delivered
//   
// For notifications:
//   PENDING → PROCESSING → SENT → DELIVERED
//
// Status transitions are ONE-WAY (mostly):
//   PENDING → PROCESSING (worker picked it up)
//   PROCESSING → SENT (provider accepted it)
//   SENT → DELIVERED (confirmed received)
//   
//   OR
//   
//   PROCESSING → PENDING (retry scheduled)
//   PROCESSING → FAILED (no more retries)
//

/**
 * Represents the current state of a notification in its lifecycle.
 * 
 * State Machine Diagram:
 * 
 *                          ┌─────────────┐
 *                          │   PENDING   │
 *                          └──────┬──────┘
 *                                 │ Worker picks up
 *                                 ▼
 *                          ┌─────────────┐
 *                          │ PROCESSING  │
 *                          └──────┬──────┘
 *                                 │
 *              ┌──────────────────┼──────────────────┐
 *              │ Success          │ Retry            │ Final failure
 *              ▼                  ▼                  ▼
 *        ┌─────────┐        ┌─────────┐        ┌─────────┐
 *        │  SENT   │        │ PENDING │        │ FAILED  │
 *        └────┬────┘        └─────────┘        └─────────┘
 *             │ Delivery confirmed
 *             ▼
 *       ┌───────────┐
 *       │ DELIVERED │
 *       └─────┬─────┘
 *             │ User reads (in-app only)
 *             ▼
 *        ┌─────────┐
 *        │  READ   │
 *        └─────────┘
 */
public enum NotificationStatus {

    // ==================== Status Values ====================
    
    /**
     * PENDING - Waiting to be processed
     * 
     * This is the initial state when a notification is created.
     * The notification is in the queue, waiting for a worker to pick it up.
     * 
     * Next states: PROCESSING (when a worker picks it up)
     */
    PENDING("pending", "Waiting in queue to be processed", true),
    
    /**
     * PROCESSING - Currently being sent
     * 
     * A worker has picked up this notification and is attempting 
     * to send it to the delivery provider (SendGrid, Twilio, etc.)
     * 
     * This is a transitional state - it shouldn't stay here long.
     * If a worker crashes, stuck PROCESSING notifications need to be
     * reset to PENDING (handled by a cleanup job).
     * 
     * Next states: 
     * - SENT (if provider accepts it)
     * - PENDING (if temporary failure, will retry)
     * - FAILED (if all retries exhausted)
     */
    PROCESSING("processing", "Currently being delivered", true),
    
    /**
     * SENT - Successfully sent to the provider
     * 
     * The delivery provider (SendGrid, Twilio, FCM) has accepted 
     * the notification. It's now in their hands.
     * 
     * Note: SENT doesn't mean DELIVERED. The provider might still
     * fail to deliver it (invalid email, phone off, etc.)
     * 
     * Next states: DELIVERED (when we get confirmation)
     */
    SENT("sent", "Sent to delivery provider", false),
    
    /**
     * DELIVERED - Confirmed delivered to the user
     * 
     * We've received confirmation that the notification reached the user.
     * For email: delivered to inbox (might be spam though)
     * For SMS: received by phone
     * For push: delivered to device
     * For in-app: stored and available in inbox
     * 
     * Next states: READ (for in-app notifications only)
     */
    DELIVERED("delivered", "Successfully delivered to recipient", false),
    
    /**
     * FAILED - All retries exhausted, giving up
     * 
     * We tried multiple times but couldn't deliver this notification.
     * Possible reasons:
     * - Invalid email/phone
     * - Provider is down for too long
     * - User blocked notifications
     * - Rate limited by provider
     * 
     * This is a TERMINAL state - no more processing will happen.
     * The error_message field will contain details.
     * 
     * Next states: None (terminal state)
     */
    FAILED("failed", "Delivery failed after all retries", false),
    
    /**
     * READ - User has read the notification
     * 
     * This status is mainly for IN_APP notifications.
     * When the user opens their notification inbox or clicks
     * on a notification, we mark it as READ.
     * 
     * This helps with:
     * - Showing unread count badges
     * - Distinguishing new vs seen notifications
     * - Analytics on engagement
     * 
     * Next states: None (terminal state)
     */
    READ("read", "Read by the recipient", false);
    
    // ==================== Enum Fields ====================
    
    /**
     * Lowercase value for API responses and database storage.
     */
    private final String value;
    
    /**
     * Human-readable description of this status.
     */
    private final String description;
    
    /**
     * Whether this notification is still being processed.
     * 
     * true = notification might still be delivered
     * false = notification is in a final or waiting state
     * 
     * Used to show users whether they should wait or not.
     */
    private final boolean processing;
    
    // ==================== Constructor ====================
    
    NotificationStatus(String value, String description, boolean processing) {
        this.value = value;
        this.description = description;
        this.processing = processing;
    }
    
    // ==================== Getter Methods ====================
    
    public String getValue() {
        return value;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if the notification is still being processed.
     * 
     * @return true if status is PENDING or PROCESSING
     */
    public boolean isProcessing() {
        return processing;
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Check if this is a terminal (final) state.
     * 
     * Terminal states are: DELIVERED, FAILED, READ
     * Once in a terminal state, no more processing happens.
     * 
     * @return true if this is a final state
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED || this == READ;
    }
    
    /**
     * Check if this notification was successfully delivered.
     * 
     * @return true if status is SENT, DELIVERED, or READ
     */
    public boolean isSuccess() {
        return this == SENT || this == DELIVERED || this == READ;
    }
    
    /**
     * Check if this notification can be retried.
     * 
     * Only PENDING notifications can be retried.
     * (PROCESSING might be stuck, needs cleanup first)
     * 
     * @return true if the notification can be retried
     */
    public boolean canRetry() {
        return this == PENDING;
    }
    
    /**
     * Convert a string to NotificationStatus enum.
     * 
     * @param value The string value (case-insensitive)
     * @return The corresponding NotificationStatus
     * @throws IllegalArgumentException If value doesn't match
     */
    public static NotificationStatus fromValue(String value) {
        for (NotificationStatus status : NotificationStatus.values()) {
            if (status.value.equalsIgnoreCase(value) || 
                status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
            "Unknown notification status: " + value
        );
    }
}
