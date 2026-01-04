package com.notification.controller;

// =====================================================
// NotificationController.java - REST API Endpoints
// =====================================================
//
// Controllers handle HTTP requests and return responses.
// They're the "front door" of our API.
//
// This controller handles all notification-related endpoints:
// - POST /api/v1/notifications - Send a notification
// - POST /api/v1/notifications/bulk - Send bulk notifications
// - GET /api/v1/notifications/{id} - Get notification by ID
// - GET /api/v1/notifications/user/{userId} - Get user's inbox
//

import com.notification.dto.request.BulkNotificationRequest;
import com.notification.dto.request.SendNotificationRequest;
import com.notification.dto.response.ApiResponse;
import com.notification.dto.response.BulkNotificationResponse;
import com.notification.dto.response.NotificationResponse;
import com.notification.dto.response.PagedResponse;
import com.notification.model.enums.ChannelType;
import com.notification.service.NotificationService;

// OpenAPI annotations for Swagger documentation
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

// Validation
import jakarta.validation.Valid;

// Spring Web imports
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for notification operations.
 * 
 * Annotations explained:
 * 
 * @RestController = @Controller + @ResponseBody
 *   - Marks this as a controller that handles REST requests
 *   - Return values are automatically serialized to JSON
 * 
 * @RequestMapping("/api/v1/notifications")
 *   - Base path for all endpoints in this controller
 *   - "v1" is version 1 of our API (good practice for future changes)
 * 
 * @Tag is for OpenAPI/Swagger documentation
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Notification management APIs")
public class NotificationController {

    private final NotificationService notificationService;
    
    /**
     * Constructor injection.
     * 
     * Spring automatically injects the NotificationService.
     */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    
    // ==================== Send Notification ====================
    
    /**
     * Send a single notification.
     * 
     * @PostMapping handles HTTP POST requests.
     * 
     * @Valid triggers validation on the request body.
     * If validation fails, Spring throws MethodArgumentNotValidException
     * which is caught by our GlobalExceptionHandler.
     * 
     * @RequestBody tells Spring to parse the JSON body into our DTO.
     * 
     * @Operation documents this endpoint for Swagger.
     */
    @PostMapping
    @Operation(
        summary = "Send a notification",
        description = "Send a notification to a user via email, SMS, push, or in-app"
    )
    public ResponseEntity<ApiResponse<NotificationResponse>> sendNotification(
            @Valid @RequestBody SendNotificationRequest request) {
        
        NotificationResponse response = notificationService.sendNotification(request);
        
        // Return 201 Created with the notification data
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Notification queued successfully", response));
    }
    
    /**
     * Send notifications to multiple users.
     */
    @PostMapping("/bulk")
    @Operation(
        summary = "Send bulk notifications",
        description = "Send the same notification to multiple users"
    )
    public ResponseEntity<ApiResponse<BulkNotificationResponse>> sendBulkNotification(
            @Valid @RequestBody BulkNotificationRequest request) {
        
        BulkNotificationResponse response = notificationService.sendBulkNotification(request);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Bulk notification processed", response));
    }
    
    // ==================== Get Notification ====================
    
    /**
     * Get a notification by ID.
     * 
     * @PathVariable extracts the {id} from the URL path.
     * 
     * URL: GET /api/v1/notifications/123-abc-456
     * {id} = "123-abc-456"
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get notification by ID",
        description = "Retrieve a specific notification by its ID"
    )
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotification(
            @PathVariable UUID id) {
        
        NotificationResponse response = notificationService.getNotificationById(id);
        
        return ResponseEntity.ok(ApiResponse.success("Notification retrieved", response));
    }
    
    // ==================== User Inbox ====================
    
    /**
     * Get notifications for a user (inbox).
     * 
     * @RequestParam extracts query parameters from the URL.
     * 
     * Example URL: GET /api/v1/notifications/user/123?page=0&size=20&channel=EMAIL
     * 
     * defaultValue sets the default if parameter is not provided.
     * required = false makes the parameter optional.
     */
    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Get user notifications",
        description = "Get all notifications for a user with pagination"
    )
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getUserNotifications(
            @PathVariable UUID userId,
            
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Filter by channel (optional)")
            @RequestParam(required = false) ChannelType channel) {
        
        // Create Pageable object for pagination
        Pageable pageable = PageRequest.of(page, size);
        
        PagedResponse<NotificationResponse> response;
        
        if (channel != null) {
            response = notificationService.getUserNotificationsByChannel(userId, channel, pageable);
        } else {
            response = notificationService.getUserNotifications(userId, pageable);
        }
        
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved", response));
    }
    
    /**
     * Get unread count for a user.
     */
    @GetMapping("/user/{userId}/unread-count")
    @Operation(
        summary = "Get unread count",
        description = "Get the number of unread in-app notifications for a user"
    )
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@PathVariable UUID userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved", count));
    }
    
    // ==================== Status Updates ====================
    
    /**
     * Mark a notification as read.
     * 
     * @PatchMapping is for partial updates.
     * We're not replacing the whole resource, just updating the status.
     */
    @PatchMapping("/{id}/read")
    @Operation(
        summary = "Mark notification as read",
        description = "Mark an in-app notification as read"
    )
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable UUID id) {
        NotificationResponse response = notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", response));
    }
    
    /**
     * Mark all notifications as read for a user.
     */
    @PatchMapping("/user/{userId}/read-all")
    @Operation(
        summary = "Mark all as read",
        description = "Mark all in-app notifications as read for a user"
    )
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(@PathVariable UUID userId) {
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(
            count + " notifications marked as read", count
        ));
    }
}
