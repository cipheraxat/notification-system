package com.notification.exception;

// =====================================================
// GlobalExceptionHandler.java - Centralized Error Handling
// =====================================================
//
// This class catches ALL exceptions thrown by controllers
// and converts them to proper HTTP responses.
//
// Without this, exceptions would result in ugly 500 errors.
// With this, we return clean, structured JSON responses.
//
// How it works:
// 1. Controller throws an exception
// 2. Spring looks for a @ExceptionHandler that matches
// 3. That handler creates the response
//
// The hierarchy:
//   Most specific handlers first, generic ones last
//

import com.notification.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the entire application.
 * 
 * @RestControllerAdvice combines:
 * - @ControllerAdvice: Applies to all controllers
 * - @ResponseBody: Return values are JSON (not views)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Logger for recording exceptions (for debugging)
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // ==================== Custom Exception Handlers ====================
    
    /**
     * Handle ResourceNotFoundException.
     * 
     * Returns: 404 Not Found
     * 
     * Example response:
     * {
     *   "success": false,
     *   "message": "User not found with id: 123"
     * }
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)  // 404
            .body(ApiResponse.error(ex.getMessage()));
    }
    
    /**
     * Handle RateLimitExceededException.
     * 
     * Returns: 429 Too Many Requests
     * 
     * Also sets the Retry-After header to tell the client
     * when they can try again.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleRateLimit(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        
        // Include retry info in the response
        Map<String, Object> details = new HashMap<>();
        details.put("channel", ex.getChannel().name());
        details.put("limit", ex.getLimit());
        details.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)  // 429
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(ApiResponse.error(ex.getMessage(), details));
    }
    
    /**
     * Handle NotificationException.
     * 
     * Returns: 500 Internal Server Error
     */
    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotificationException(NotificationException ex) {
        log.error("Notification error: {}", ex.getMessage(), ex);
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)  // 500
            .body(ApiResponse.error(ex.getMessage()));
    }
    
    // ==================== Validation Exception Handlers ====================
    
    /**
     * Handle validation errors from @Valid on request bodies.
     * 
     * When a @Valid request body has validation errors (like @NotNull, @NotBlank),
     * Spring throws MethodArgumentNotValidException.
     * 
     * Returns: 400 Bad Request with validation error details
     * 
     * Example response:
     * {
     *   "success": false,
     *   "message": "Validation failed",
     *   "data": {
     *     "userId": "User ID is required",
     *     "channel": "Channel is required"
     *   }
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        
        // Collect all field errors into a map
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        
        log.warn("Validation failed: {}", errors);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)  // 400
            .body(ApiResponse.error("Validation failed", errors));
    }
    
    /**
     * Handle constraint violations (like @NotNull on path variables).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            errors.put(fieldName, violation.getMessage());
        });
        
        log.warn("Constraint violation: {}", errors);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed", errors));
    }
    
    /**
     * Handle IllegalArgumentException.
     * 
     * Often thrown when enum conversion fails or invalid parameters.
     * 
     * Returns: 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage()));
    }
    
    // ==================== Catch-All Handler ====================
    
    /**
     * Handle any other exceptions.
     * 
     * This is the last resort for unexpected errors.
     * We log the full stack trace for debugging but return
     * a generic message to the user (don't expose internals).
     * 
     * Returns: 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        // Log the full exception with stack trace
        log.error("Unexpected error occurred", ex);
        
        // Return a generic message (don't expose internal details)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}
