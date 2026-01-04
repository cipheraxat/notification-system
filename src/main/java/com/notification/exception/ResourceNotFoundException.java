package com.notification.exception;

// =====================================================
// ResourceNotFoundException.java - Custom Exception
// =====================================================
//
// Custom exceptions make error handling cleaner and more specific.
// 
// Instead of:
//   throw new RuntimeException("User not found");
// 
// We use:
//   throw new ResourceNotFoundException("User", "id", userId);
// 
// Benefits:
// 1. More descriptive error messages
// 2. Can be caught specifically (catch ResourceNotFoundException)
// 3. Can have custom HTTP status codes
// 4. Better logging and debugging
//

/**
 * Exception thrown when a requested resource doesn't exist.
 * 
 * Examples:
 * - User not found
 * - Template not found
 * - Notification not found
 * 
 * This exception will be caught by our GlobalExceptionHandler
 * and converted to a 404 Not Found HTTP response.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * The name of the resource type that wasn't found.
     * Example: "User", "Template", "Notification"
     */
    private final String resourceName;
    
    /**
     * The name of the field used to search.
     * Example: "id", "email", "name"
     */
    private final String fieldName;
    
    /**
     * The value that was searched for.
     * Example: "123-abc-456", "john@example.com"
     */
    private final Object fieldValue;
    
    /**
     * Create a new ResourceNotFoundException.
     * 
     * Usage:
     *   throw new ResourceNotFoundException("User", "id", userId);
     *   → "User not found with id: 123-abc-456"
     * 
     * @param resourceName The type of resource
     * @param fieldName The field searched by
     * @param fieldValue The value searched for
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        // Call parent constructor with the formatted message
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
    
    /**
     * Simple constructor with just a message.
     * 
     * Usage:
     *   throw new ResourceNotFoundException("User not found");
     */
    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceName = null;
        this.fieldName = null;
        this.fieldValue = null;
    }
    
    // Getters
    public String getResourceName() { return resourceName; }
    public String getFieldName() { return fieldName; }
    public Object getFieldValue() { return fieldValue; }
}
