package com.notification.dto.request;

// =====================================================
// BulkNotificationRequest.java - Bulk Send Request
// =====================================================
//
// Sometimes you need to send the same notification to
// multiple users. Instead of making 100 API calls,
// you can make 1 bulk request.
//
// Example use cases:
// - Marketing campaign to all users
// - System maintenance announcement
// - New feature announcement
//

import com.notification.model.enums.ChannelType;
import com.notification.model.enums.Priority;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for sending notifications to multiple users.
 * 
 * Example JSON request:
 * {
 *   "userIds": [
 *     "550e8400-e29b-41d4-a716-446655440001",
 *     "550e8400-e29b-41d4-a716-446655440002"
 *   ],
 *   "channel": "EMAIL",
 *   "priority": "LOW",
 *   "templateName": "system-announcement",
 *   "templateVariables": {
 *     "announcementTitle": "New Feature!",
 *     "announcementBody": "We just launched..."
 *   }
 * }
 * 
 * Note: For personalization (like {{userName}}), each user
 * would need their own request. Bulk is for generic messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkNotificationRequest {

    /**
     * List of user IDs to send notifications to.
     * 
     * @NotEmpty ensures the list is not null AND not empty.
     * (NotNull only checks for null, not empty list)
     */
    @NotEmpty(message = "At least one user ID is required")
    private List<UUID> userIds;
    
    /**
     * Channel to use for all notifications.
     */
    @NotNull(message = "Channel is required")
    private ChannelType channel;
    
    /**
     * Priority level (defaults to LOW for bulk).
     * 
     * Bulk notifications are usually marketing/announcements,
     * which are typically low priority.
     */
    @Builder.Default
    private Priority priority = Priority.LOW;
    
    // ==================== Content Options ====================
    
    /**
     * Template name (if using template).
     */
    private String templateName;
    
    /**
     * Template variables (same for all users).
     * 
     * For user-specific variables, you'd need to use
     * individual SendNotificationRequest calls.
     */
    @Builder.Default
    private Map<String, String> templateVariables = new HashMap<>();
    
    /**
     * Direct subject (if not using template).
     */
    private String subject;
    
    /**
     * Direct content (if not using template).
     */
    private String content;
    
    /**
     * Check if using template.
     */
    public boolean isTemplateRequest() {
        return templateName != null && !templateName.isBlank();
    }
}
