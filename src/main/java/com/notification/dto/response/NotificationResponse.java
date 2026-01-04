package com.notification.dto.response;

// =====================================================
// NotificationResponse.java - API Response DTO
// =====================================================
//
// This is what we return to the API consumer.
// 
// We don't return the Entity directly because:
// 1. Entities might have sensitive data
// 2. Entities have lazy-loaded relationships (causes errors)
// 3. We want control over the JSON structure
// 4. Different endpoints might need different fields
//

import com.notification.model.entity.Notification;
import com.notification.model.enums.ChannelType;
import com.notification.model.enums.NotificationStatus;
import com.notification.model.enums.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for notification data.
 * 
 * Example JSON response:
 * {
 *   "id": "abc123-...",
 *   "userId": "user456-...",
 *   "channel": "EMAIL",
 *   "priority": "HIGH",
 *   "subject": "Your OTP Code",
 *   "content": "Your code is 123456",
 *   "status": "SENT",
 *   "retryCount": 0,
 *   "createdAt": "2024-01-15T10:30:00Z",
 *   "sentAt": "2024-01-15T10:30:05Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private UUID userId;
    private ChannelType channel;
    private Priority priority;
    private String subject;
    private String content;
    private NotificationStatus status;
    private int retryCount;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime sentAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime readAt;
    
    // ==================== Factory Method ====================
    
    /**
     * Create a NotificationResponse from a Notification entity.
     * 
     * This is a common pattern called "Factory Method".
     * It converts an internal Entity to an external DTO.
     * 
     * Usage:
     *   Notification entity = notificationRepository.findById(id);
     *   NotificationResponse dto = NotificationResponse.from(entity);
     *   return ResponseEntity.ok(dto);
     * 
     * @param notification The entity to convert
     * @return A DTO with the relevant fields
     */
    public static NotificationResponse from(Notification notification) {
        if (notification == null) {
            return null;
        }
        
        return NotificationResponse.builder()
            .id(notification.getId())
            // Get the user ID without loading the full User object
            // This is why we use LAZY loading on relationships
            .userId(notification.getUser() != null ? notification.getUser().getId() : null)
            .channel(notification.getChannel())
            .priority(notification.getPriority())
            .subject(notification.getSubject())
            .content(notification.getContent())
            .status(notification.getStatus())
            .retryCount(notification.getRetryCount())
            .errorMessage(notification.getErrorMessage())
            .createdAt(notification.getCreatedAt())
            .sentAt(notification.getSentAt())
            .deliveredAt(notification.getDeliveredAt())
            .readAt(notification.getReadAt())
            .build();
    }
}
