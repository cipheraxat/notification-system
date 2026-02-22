package com.notification.model.entity;

// =====================================================
// Notification.java - The Core Notification Entity
// =====================================================
//
// This is the HEART of our notification system!
// Every notification that goes through our system is stored here.
//
// A Notification represents a single message to a single user
// through a single channel. If you send the same message via
// email AND SMS, that's TWO notification records.
//

import com.notification.model.enums.ChannelType;
import com.notification.model.enums.NotificationStatus;
import com.notification.model.enums.Priority;
import com.notification.util.UuidGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Notification Entity - Represents a single notification message.
 * 
 * Maps to the 'notifications' table.
 * 
 * Lifecycle:
 * 1. Created with status PENDING
 * 2. Worker picks it up → status becomes PROCESSING
 * 3. Sent to provider → status becomes SENT
 * 4. Confirmed delivered → status becomes DELIVERED
 * 5. If failed, might retry (back to PENDING) or give up (FAILED)
 * 
 * This is the busiest table in our system, so we have many indexes
 * to optimize common queries.
 */
@Entity
@Table(
    name = "notifications",
    // Indexes for common query patterns
    indexes = {
        // Find notifications for a specific user (for inbox)
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        
        // Filter by status (for processing PENDING notifications)
        @Index(name = "idx_notifications_status", columnList = "status"),
        
        // Filter by channel (for analytics)
        @Index(name = "idx_notifications_channel", columnList = "channel"),
        
        // Sort user's notifications by time (for inbox pagination)
        @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at DESC")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    // ==================== Recipient Information ====================
    
    /**
     * The user who will receive this notification.
     * 
     * We use LAZY loading because we often process notifications
     * in bulk and don't need the full User object.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    /**
     * The template used for this notification (optional).
     * 
     * If null, the notification was sent with custom content.
     * We keep this reference for audit purposes.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private NotificationTemplate template;
    
    // ==================== Delivery Settings ====================
    
    /**
     * How to deliver this notification.
     * EMAIL, SMS, PUSH, or IN_APP
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;
    
    /**
     * How urgently should this be processed?
     * 
     * HIGH - Process immediately (OTP, security alerts)
     * MEDIUM - Normal order (order confirmations)
     * LOW - Can wait (marketing, newsletters)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;
    
    // ==================== Notification Content ====================
    
    /**
     * Subject line (mainly for email and push).
     * 
     * This is the PROCESSED subject (placeholders already replaced).
     */
    @Column(name = "subject", length = 500)
    private String subject;
    
    /**
     * The main message content.
     * 
     * This is the PROCESSED content (placeholders already replaced).
     * Stored as TEXT to allow long messages (especially for email).
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    // ==================== Status & Tracking ====================
    
    /**
     * Current status of this notification.
     * See NotificationStatus enum for the full state machine.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;
    
    /**
     * How many times we've tried to send this notification.
     * 
     * Starts at 0. Incremented each time we attempt delivery.
     */
    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;
    
    /**
     * Maximum number of retry attempts.
     * 
     * After this many failures, we mark it as FAILED.
     * Different priorities might have different max retries.
     */
    @Column(name = "max_retries")
    @Builder.Default
    private int maxRetries = 3;
    
    /**
     * When to try sending again.
     * 
     * Set when a delivery attempt fails and we want to retry.
     * Uses exponential backoff: 1min, 5min, 25min, etc.
     * 
     * Null means no retry scheduled.
     */
    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;
    
    /**
     * Error message from the last failed attempt.
     * 
     * Helps with debugging and support.
     * Examples: "Invalid email address", "Rate limited", "Provider timeout"
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    // ==================== Timestamps ====================
    
    /**
     * When this notification was successfully sent to the provider.
     */
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;
    
    /**
     * When delivery was confirmed.
     */
    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;
    
    /**
     * When the user read this notification (in-app only).
     */
    @Column(name = "read_at")
    private OffsetDateTime readAt;
    
    /**
     * When this notification was created.
     */
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UuidGenerator.newV7();
        }
        this.createdAt = OffsetDateTime.now();
    }
    
    // ==================== Status Update Methods ====================
    
    /**
     * Mark this notification as being processed.
     * Called when a worker picks it up for delivery.
     */
    public void markAsProcessing() {
        this.status = NotificationStatus.PROCESSING;
    }
    
    /**
     * Mark this notification as sent.
     * Called when the provider accepts the message.
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = OffsetDateTime.now();
        this.errorMessage = null; // Clear any previous errors
    }
    
    /**
     * Mark this notification as delivered.
     * Called when we get delivery confirmation.
     */
    public void markAsDelivered() {
        this.status = NotificationStatus.DELIVERED;
        this.deliveredAt = OffsetDateTime.now();
    }
    
    /**
     * Mark this notification as read (in-app only).
     * Called when the user views the notification.
     */
    public void markAsRead() {
        this.status = NotificationStatus.READ;
        this.readAt = OffsetDateTime.now();
    }
    
    /**
     * Mark this notification as failed.
     * Called when all retries are exhausted.
     * 
     * @param errorMessage Description of what went wrong
     */
    public void markAsFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
    }
    
    /**
     * Schedule a retry for this notification.
     * 
     * Uses exponential backoff:
     * - Retry 1: 1 minute delay
     * - Retry 2: 5 minutes (1 * 5)
     * - Retry 3: 25 minutes (5 * 5)
     * 
     * @param errorMessage Description of why we need to retry
     */
    public void scheduleRetry(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;
        
        if (this.retryCount >= this.maxRetries) {
            // No more retries, mark as failed
            markAsFailed("Max retries exceeded. Last error: " + errorMessage);
        } else {
            // Schedule next retry with exponential backoff
            long delayMinutes = (long) Math.pow(5, this.retryCount);
            this.nextRetryAt = OffsetDateTime.now().plusMinutes(delayMinutes);
            this.status = NotificationStatus.PENDING;
        }
    }
    
    // ==================== Query Helper Methods ====================
    
    /**
     * Check if this notification can be retried.
     * 
     * @return true if status is PENDING and retry time has passed
     */
    public boolean isReadyForRetry() {
        if (this.status != NotificationStatus.PENDING) {
            return false;
        }
        if (this.nextRetryAt == null) {
            return true; // Never been tried
        }
        return OffsetDateTime.now().isAfter(this.nextRetryAt);
    }
    
    /**
     * Check if this is a new notification (never attempted).
     * 
     * @return true if this notification has never been processed
     */
    public boolean isNew() {
        return this.status == NotificationStatus.PENDING && this.retryCount == 0;
    }
}
