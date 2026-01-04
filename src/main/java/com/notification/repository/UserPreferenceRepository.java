package com.notification.repository;

// =====================================================
// UserPreferenceRepository.java - Preferences Data Access
// =====================================================

import com.notification.model.entity.UserPreference;
import com.notification.model.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserPreference entity operations.
 */
@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {

    /**
     * Find all preferences for a user.
     * 
     * Returns a list because each user can have multiple preferences
     * (one per channel).
     */
    List<UserPreference> findByUserId(UUID userId);
    
    /**
     * Find a specific preference for a user and channel.
     * 
     * This is used before sending a notification to check:
     * 1. Is this channel enabled?
     * 2. Are we in quiet hours?
     */
    Optional<UserPreference> findByUserIdAndChannel(UUID userId, ChannelType channel);
    
    /**
     * Check if a user has enabled a specific channel.
     * 
     * More efficient than loading the full preference object
     * when we only need a yes/no answer.
     */
    boolean existsByUserIdAndChannelAndEnabledTrue(UUID userId, ChannelType channel);
    
    /**
     * Delete all preferences for a user.
     * 
     * Used when resetting a user's preferences to defaults.
     */
    void deleteByUserId(UUID userId);
}
