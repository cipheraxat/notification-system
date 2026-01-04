package com.notification.dto.response;

// =====================================================
// BulkNotificationResponse.java - Bulk Operation Response
// =====================================================
//
// When sending bulk notifications, we return a summary
// of how many succeeded vs failed.
//

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for bulk notification operations.
 * 
 * Example JSON response:
 * {
 *   "totalRequested": 100,
 *   "successCount": 98,
 *   "failedCount": 2,
 *   "successfulIds": ["abc...", "def...", ...],
 *   "failedIds": ["xyz...", "123..."],
 *   "errors": [
 *     {"userId": "xyz...", "error": "User not found"},
 *     {"userId": "123...", "error": "Channel disabled"}
 *   ]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkNotificationResponse {

    /**
     * Total number of notifications requested.
     */
    private int totalRequested;
    
    /**
     * Number of notifications successfully queued.
     */
    private int successCount;
    
    /**
     * Number of notifications that failed.
     */
    private int failedCount;
    
    /**
     * IDs of successfully created notifications.
     */
    @Builder.Default
    private List<UUID> successfulIds = new ArrayList<>();
    
    /**
     * IDs of users for whom notification failed.
     */
    @Builder.Default
    private List<UUID> failedUserIds = new ArrayList<>();
    
    /**
     * Detailed error information.
     */
    @Builder.Default
    private List<ErrorDetail> errors = new ArrayList<>();
    
    /**
     * Inner class for error details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private UUID userId;
        private String error;
    }
    
    /**
     * Add a successful notification to the response.
     */
    public void addSuccess(UUID notificationId) {
        this.successfulIds.add(notificationId);
        this.successCount++;
    }
    
    /**
     * Add a failed notification to the response.
     */
    public void addFailure(UUID userId, String error) {
        this.failedUserIds.add(userId);
        this.errors.add(ErrorDetail.builder()
            .userId(userId)
            .error(error)
            .build());
        this.failedCount++;
    }
}
