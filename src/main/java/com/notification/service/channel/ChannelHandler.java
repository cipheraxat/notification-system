package com.notification.service.channel;

// =====================================================
// ChannelHandler.java - Interface for Delivery Channels
// =====================================================
//
// This is an INTERFACE - it defines WHAT a channel handler can do,
// but not HOW it does it.
//
// Each channel (email, SMS, push, in-app) implements this interface
// with their own specific logic.
//
// This is called the STRATEGY PATTERN:
// - Common interface for all strategies
// - Different implementations for different behaviors
// - Easy to add new channels without changing existing code
//

import com.notification.model.entity.Notification;
import com.notification.model.enums.ChannelType;

/**
 * Interface for notification channel handlers.
 * 
 * Each channel (EMAIL, SMS, PUSH, IN_APP) has a handler
 * that implements this interface.
 */
public interface ChannelHandler {

    /**
     * Get the channel type this handler supports.
     * 
     * Used by the dispatcher to route notifications
     * to the correct handler.
     * 
     * @return The channel type (EMAIL, SMS, PUSH, IN_APP)
     */
    ChannelType getChannelType();
    
    /**
     * Send the notification.
     * 
     * This method contains the actual logic for sending
     * the notification via this channel.
     * 
     * @param notification The notification to send
     * @return true if sent successfully, false otherwise
     */
    boolean send(Notification notification);
    
    /**
     * Check if this handler can process the notification.
     * 
     * Validates that the notification has the required data
     * for this channel (e.g., email address for EMAIL channel).
     * 
     * @param notification The notification to validate
     * @return true if the notification can be processed
     */
    default boolean canHandle(Notification notification) {
        return notification != null && 
               notification.getChannel() == getChannelType();
    }
}
