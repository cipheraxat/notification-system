package com.notification.repository;

// =====================================================
// NotificationRepository.java - Data Access for Notifications
// =====================================================
//
// This is the most important repository!
// It handles all notification queries.
//

import com.notification.model.entity.Notification;
import com.notification.model.enums.ChannelType;
import com.notification.model.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Notification entity operations.
 * 
 * This repository has more complex queries because notifications
 * are the core of our system and we query them in many ways.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // ==================== User Inbox Queries ====================
    
    /**
     * Find notifications for a user, ordered by creation time.
     * 
     * This is for the user's notification inbox.
     * 
     * Parameters:
     * - userId: The user whose notifications to find
     * - pageable: Pagination info (page number, size, sorting)
     * 
     * Returns Page<Notification> for pagination support.
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find notifications for a user filtered by channel.
     * 
     * Example: Show only email notifications
     */
    Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(
        UUID userId, 
        ChannelType channel, 
        Pageable pageable
    );
    
    /**
     * Find notifications for a user filtered by status.
     * 
     * Example: Show only delivered notifications
     */
    Page<Notification> findByUserIdAndStatusOrderByCreatedAtDesc(
        UUID userId, 
        NotificationStatus status, 
        Pageable pageable
    );
    
    /**
     * Find notifications by status and channel (for admin/monitoring).
     * 
     * Used to check queue status for specific channels.
     */
    Page<Notification> findByStatusAndChannelOrderByCreatedAtDesc(
        NotificationStatus status, 
        ChannelType channel, 
        Pageable pageable
    );
    
    /**
     * Find notifications by status (for admin/monitoring).
     */
    Page<Notification> findByStatusOrderByCreatedAtDesc(
        NotificationStatus status, 
        Pageable pageable
    );
    
    /**
     * Count unread notifications for a user (in-app only).
     * 
     * Used for showing the "unread badge" count.
     * 
     * A notification is "unread" if:
     * - Channel is IN_APP (only in-app has read tracking)
     * - Status is DELIVERED (sent but not read)
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId " +
           "AND n.channel = 'IN_APP' AND n.status = 'DELIVERED'")
    long countUnreadForUser(@Param("userId") UUID userId);
    
    // ==================== Processing Queries ====================
    
    /**
     * Find notifications that are ready to be processed.
     * 
     * A notification is ready if:
     * - Status is PENDING
     * - No scheduled retry time OR retry time has passed
     * 
     * We order by priority (HIGH first) then by creation time.
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'PENDING' " +
           "AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now) " +
           "ORDER BY n.priority ASC, n.createdAt ASC")
    List<Notification> findReadyForProcessing(
        @Param("now") OffsetDateTime now, 
        Pageable pageable
    );
    
    /**
     * Find stuck notifications.
     * 
     * A notification is "stuck" if it's been PROCESSING for too long
     * (worker crashed or something went wrong).
     * 
     * We reset these to PENDING so they can be processed again.
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'PROCESSING' " +
           "AND n.createdAt < :cutoffTime")
    List<Notification> findStuckNotifications(@Param("cutoffTime") OffsetDateTime cutoffTime);
    
    /**
     * Find notifications that need retry.
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'PENDING' " +
           "AND n.nextRetryAt IS NOT NULL AND n.nextRetryAt <= :now")
    List<Notification> findDueForRetry(@Param("now") OffsetDateTime now);
    
    // ==================== Bulk Updates ====================
    //
    // @Modifying tells Spring this query changes data (not just reads).
    // It's required for UPDATE/DELETE queries.
    //
    
    /**
     * Reset stuck notifications to PENDING status.
     * 
     * Called by a scheduled cleanup job to handle crashed workers.
     * 
     * @return Number of notifications reset
     */
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'PENDING' " +
           "WHERE n.status = 'PROCESSING' AND n.createdAt < :cutoffTime")
    int resetStuckNotifications(@Param("cutoffTime") OffsetDateTime cutoffTime);
    
    /**
     * Mark all notifications as read for a user (in-app).
     * 
     * Used for "Mark all as read" feature.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :now " +
           "WHERE n.user.id = :userId AND n.channel = 'IN_APP' " +
           "AND n.status = 'DELIVERED'")
    int markAllAsReadForUser(
        @Param("userId") UUID userId, 
        @Param("now") OffsetDateTime now
    );
    
    // ==================== Analytics Queries ====================
    
    /**
     * Count notifications by status.
     * 
     * Useful for dashboard: "500 pending, 10000 delivered, 50 failed"
     */
    long countByStatus(NotificationStatus status);
    
    /**
     * Count notifications by channel.
     */
    long countByChannel(ChannelType channel);
    
    /**
     * Count notifications created after a certain time.
     * 
     * Useful for: "1000 notifications in the last hour"
     */
    long countByCreatedAtAfter(OffsetDateTime time);
    
    /**
     * Count notifications for a user by channel since a given time.
     * 
     * Used for rate limiting: "How many emails did we send to this
     * user in the last hour?"
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId " +
           "AND n.channel = :channel AND n.createdAt >= :since " +
           "AND n.status NOT IN ('FAILED')")
    long countByUserIdAndChannelSince(
        @Param("userId") UUID userId,
        @Param("channel") ChannelType channel,
        @Param("since") OffsetDateTime since
    );

    /**
     * Find notification by ID with user eagerly loaded.
     * Used for processing notifications where we need to access user properties.
     */
    @Query("SELECT n FROM Notification n JOIN FETCH n.user WHERE n.id = :id")
    Optional<Notification> findByIdWithUser(@Param("id") UUID id);
}
