package com.notification.exception;

// =====================================================
// NotificationException.java - General Notification Exception
// =====================================================
//
// Base exception for notification-related errors.
//

/**
 * General exception for notification processing errors.
 * 
 * Examples:
 * - Failed to send to provider
 * - Template processing error
 * - Invalid notification content
 */
public class NotificationException extends RuntimeException {

    /**
     * Create with just a message.
     */
    public NotificationException(String message) {
        super(message);
    }
    
    /**
     * Create with a message and cause.
     * 
     * The cause is the original exception that triggered this one.
     * This preserves the stack trace for debugging.
     */
    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
