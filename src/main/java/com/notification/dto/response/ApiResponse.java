package com.notification.dto.response;

// =====================================================
// ApiResponse.java - Standard API Response Wrapper
// =====================================================
//
// A consistent wrapper for all API responses.
// This makes it easier for API consumers because
// every response has the same structure.
//
// Success response:
// {
//   "success": true,
//   "message": "Notification sent successfully",
//   "data": { ... actual data ... },
//   "timestamp": "2024-01-15T10:30:00Z"
// }
//
// Error response:
// {
//   "success": false,
//   "message": "User not found",
//   "data": null,
//   "timestamp": "2024-01-15T10:30:00Z"
// }
//

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Generic API response wrapper.
 * 
 * @param <T> The type of data in the response
 * 
 * Generic Types Explained:
 * - <T> is a placeholder for any type
 * - ApiResponse<NotificationResponse> → data field is NotificationResponse
 * - ApiResponse<List<NotificationResponse>> → data field is a list
 * - ApiResponse<String> → data field is a string
 * 
 * This allows us to use the same wrapper for any response type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // Don't include null fields in JSON
public class ApiResponse<T> {

    /**
     * Whether the request was successful.
     */
    private boolean success;
    
    /**
     * Human-readable message about the result.
     */
    private String message;
    
    /**
     * The actual response data (null for errors).
     */
    private T data;
    
    /**
     * When this response was generated.
     */
    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();
    
    // ==================== Factory Methods ====================
    
    /**
     * Create a success response with data.
     * 
     * Usage:
     *   return ApiResponse.success("Notification sent", notificationDto);
     * 
     * @param message Success message
     * @param data The response data
     * @return A success ApiResponse
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(OffsetDateTime.now())
            .build();
    }
    
    /**
     * Create a success response without data.
     * 
     * Usage:
     *   return ApiResponse.success("Notification deleted");
     * 
     * @param message Success message
     * @return A success ApiResponse with null data
     */
    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }
    
    /**
     * Create an error response.
     * 
     * Usage:
     *   return ApiResponse.error("User not found");
     * 
     * @param message Error message
     * @return An error ApiResponse
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .data(null)
            .timestamp(OffsetDateTime.now())
            .build();
    }
    
    /**
     * Create an error response with data (e.g., validation errors).
     * 
     * Usage:
     *   return ApiResponse.error("Validation failed", validationErrors);
     * 
     * @param message Error message
     * @param data Error details
     * @return An error ApiResponse
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .data(data)
            .timestamp(OffsetDateTime.now())
            .build();
    }
}
