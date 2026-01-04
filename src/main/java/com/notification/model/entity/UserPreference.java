package com.notification.model.entity;

// =====================================================
// UserPreference.java - User Notification Preferences
// =====================================================
//
// This entity stores per-channel preferences for each user.
// Each user can configure:
// - Which channels they want enabled/disabled
// - Quiet hours (don't disturb during certain times)
//

import com.notification.model.enums.ChannelType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * UserPreference Entity - Stores notification preferences per channel.
 * 
 * Maps to the 'user_preferences' table.
 * 
 * Example data:
 * | user_id | channel | enabled | quiet_hours_start | quiet_hours_end |
 * |---------|---------|---------|-------------------|-----------------|
 * | uuid1   | EMAIL   | true    | NULL              | NULL            |
 * | uuid1   | SMS     | true    | 22:00             | 08:00           |
 * | uuid1   | PUSH    | false   | NULL              | NULL            |
 */
@Entity
@Table(
    name = "user_preferences",
    // This constraint ensures each user has only ONE preference per channel
    // Without this, we could accidentally have multiple EMAIL preferences for one user
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_channel",
        columnNames = {"user_id", "channel"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    // ==================== Relationship to User ====================
    
    /**
     * The user this preference belongs to.
     * 
     * @ManyToOne - Many preferences belong to one user
     * 
     * @JoinColumn - Specifies the foreign key column
     *   name = "user_id" - The column name in user_preferences table
     *   nullable = false - Every preference must have a user
     * 
     * fetch = FetchType.LAZY - Don't load the full User object
     *   unless we explicitly access it. This saves memory and
     *   reduces database queries when we only need the preference.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    // ==================== Channel Settings ====================
    
    /**
     * Which channel this preference is for.
     * 
     * @Enumerated(EnumType.STRING) - Store the enum as a string
     *   in the database, not as a number.
     * 
     *   EnumType.STRING: stores "EMAIL", "SMS", "PUSH"
     *   EnumType.ORDINAL: stores 0, 1, 2 (DON'T USE - fragile!)
     * 
     * Why STRING is better:
     * - More readable in the database
     * - Safe if you reorder enum values
     * - Safe if you add new values in the middle
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;
    
    /**
     * Is this channel enabled for notifications?
     * 
     * true = send notifications via this channel
     * false = don't send via this channel
     * 
     * Default: true (opt-out model)
     */
    @Column(name = "enabled")
    @Builder.Default
    private boolean enabled = true;
    
    // ==================== Quiet Hours (Do Not Disturb) ====================
    
    /**
     * Start time for quiet hours.
     * 
     * LocalTime represents time without date (e.g., 22:00:00).
     * During quiet hours, we might:
     * - Delay non-urgent notifications
     * - Only send HIGH priority notifications
     * - Queue notifications for later
     * 
     * If null, quiet hours are not set.
     */
    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;
    
    /**
     * End time for quiet hours.
     * 
     * Example: If start=22:00 and end=08:00,
     * quiet hours are from 10 PM to 8 AM.
     */
    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;
    
    // ==================== Timestamps ====================
    
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Check if we're currently in quiet hours.
     * 
     * Handles the case where quiet hours span midnight:
     * - start=22:00, end=08:00 means 10 PM to 8 AM next day
     * 
     * @return true if current time is within quiet hours
     */
    public boolean isInQuietHours() {
        // If quiet hours not set, return false
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        
        LocalTime now = LocalTime.now();
        
        // Case 1: Quiet hours don't span midnight
        // Example: 14:00 to 16:00
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return now.isAfter(quietHoursStart) && now.isBefore(quietHoursEnd);
        }
        
        // Case 2: Quiet hours span midnight
        // Example: 22:00 to 08:00
        // True if now is after 22:00 OR before 08:00
        return now.isAfter(quietHoursStart) || now.isBefore(quietHoursEnd);
    }
    
    /**
     * Check if notifications should be sent via this channel.
     * 
     * Considers both the enabled flag and quiet hours.
     * 
     * @return true if notifications should be sent
     */
    public boolean shouldSendNotification() {
        return enabled && !isInQuietHours();
    }
}
