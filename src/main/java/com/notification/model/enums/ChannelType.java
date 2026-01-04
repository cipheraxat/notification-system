package com.notification.model.enums;

// =====================================================
// ChannelType.java - Notification Delivery Channels
// =====================================================
//
// An enum (enumeration) is a special type that represents 
// a fixed set of constants. Perfect for things like:
// - Days of the week (MONDAY, TUESDAY, ...)
// - Order status (PENDING, SHIPPED, DELIVERED, ...)
// - Notification channels (EMAIL, SMS, PUSH, IN_APP)
//
// Why use enums instead of Strings?
// 1. Type safety: Compiler catches typos
//    - ChannelType.EMAIL ✓ (compiler checks this exists)
//    - "EMAL" ✗ (typo, but String won't catch it)
// 2. IDE autocomplete: Press Ctrl+Space to see all options
// 3. Refactoring: Rename in one place, updates everywhere
//

/**
 * Represents the different ways we can send notifications.
 * 
 * Each channel has different:
 * - Rate limits (how many per hour)
 * - Delivery mechanisms (external APIs)
 * - Content formats (email has subject, SMS is short, etc.)
 */
public enum ChannelType {

    // ==================== Available Channels ====================
    
    /**
     * EMAIL - Electronic mail
     * 
     * Use case: Detailed notifications, documents, receipts
     * Provider: SendGrid, Amazon SES, Mailgun
     * Rate limit: 10 per hour per user
     * 
     * Pros:
     * - Rich content (HTML, attachments)
     * - Formal communication
     * - User can read at any time
     * 
     * Cons:
     * - Can go to spam
     * - Not instant (user might not check often)
     */
    EMAIL("email", "Email notifications via SMTP or email service"),
    
    /**
     * SMS - Short Message Service (text messages)
     * 
     * Use case: Urgent alerts, OTP codes, time-sensitive info
     * Provider: Twilio, Nexmo, AWS SNS
     * Rate limit: 5 per hour per user (costly!)
     * 
     * Pros:
     * - Very high open rate (~98%)
     * - Works without internet
     * - Reaches basic phones
     * 
     * Cons:
     * - Expensive (per-message cost)
     * - Character limit (160 chars for standard SMS)
     */
    SMS("sms", "Text messages via SMS gateway"),
    
    /**
     * PUSH - Mobile push notifications
     * 
     * Use case: Real-time alerts, promotions, engagement
     * Provider: Firebase Cloud Messaging (FCM), Apple Push Notification Service (APNs)
     * Rate limit: 20 per hour per user
     * 
     * Pros:
     * - Free (no per-message cost)
     * - Real-time delivery
     * - Rich content (images, buttons)
     * 
     * Cons:
     * - User can disable
     * - Requires app installed
     * - Device token can become invalid
     */
    PUSH("push", "Mobile push notifications via FCM/APNs"),
    
    /**
     * IN_APP - In-application notifications
     * 
     * Use case: Activity feed, inbox messages, system alerts
     * Rate limit: 100 per hour per user (most relaxed)
     * 
     * Pros:
     * - Complete control
     * - No external provider needed
     * - Persistent history
     * 
     * Cons:
     * - User must open the app
     * - Not real-time (unless using WebSocket)
     */
    IN_APP("in_app", "In-application notification inbox");
    
    // ==================== Enum Fields ====================
    
    /**
     * A lowercase identifier for the channel.
     * Useful for API responses, logging, and database storage.
     * Example: "email", "sms", "push", "in_app"
     */
    private final String value;
    
    /**
     * Human-readable description of the channel.
     * Useful for documentation and debugging.
     */
    private final String description;
    
    // ==================== Constructor ====================
    
    /**
     * Enum constructor - called once for each enum value.
     * 
     * Note: Enum constructors are implicitly private.
     * You cannot do: new ChannelType("custom", "...");
     * You can only use the predefined values.
     * 
     * @param value       The lowercase identifier
     * @param description The human-readable description
     */
    ChannelType(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    // ==================== Getter Methods ====================
    
    /**
     * Get the lowercase value of this channel.
     * 
     * Usage: ChannelType.EMAIL.getValue() → "email"
     * 
     * @return The lowercase channel identifier
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Get the description of this channel.
     * 
     * Usage: ChannelType.EMAIL.getDescription() → "Email notifications..."
     * 
     * @return The human-readable description
     */
    public String getDescription() {
        return description;
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Convert a string value to a ChannelType enum.
     * 
     * This is useful when receiving channel type from API requests.
     * The input is case-insensitive: "email", "EMAIL", "Email" all work.
     * 
     * Usage:
     *   ChannelType type = ChannelType.fromValue("email");
     *   // type is now ChannelType.EMAIL
     * 
     * @param value The string value to convert (case-insensitive)
     * @return The corresponding ChannelType
     * @throws IllegalArgumentException If the value doesn't match any channel
     */
    public static ChannelType fromValue(String value) {
        // Loop through all enum values
        for (ChannelType type : ChannelType.values()) {
            // Compare ignoring case
            if (type.value.equalsIgnoreCase(value) || 
                type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        // If no match found, throw an exception
        throw new IllegalArgumentException(
            "Unknown channel type: " + value + 
            ". Valid values are: EMAIL, SMS, PUSH, IN_APP"
        );
    }
}
