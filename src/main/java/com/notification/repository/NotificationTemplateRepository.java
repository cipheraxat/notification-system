package com.notification.repository;

// =====================================================
// NotificationTemplateRepository.java - Template Data Access
// =====================================================

import com.notification.model.entity.NotificationTemplate;
import com.notification.model.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for NotificationTemplate entity operations.
 */
@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    /**
     * Find a template by its unique name.
     * 
     * This is the main way we look up templates because API requests
     * use template names (like "welcome-email") not UUIDs.
     */
    Optional<NotificationTemplate> findByName(String name);
    
    /**
     * Find a template by name, but only if it's active.
     * 
     * Inactive templates shouldn't be used for new notifications.
     */
    Optional<NotificationTemplate> findByNameAndIsActiveTrue(String name);
    
    /**
     * Find all templates for a specific channel.
     * 
     * Useful for admin UI: "Show me all email templates"
     */
    List<NotificationTemplate> findByChannel(ChannelType channel);
    
    /**
     * Find all active templates for a channel.
     */
    List<NotificationTemplate> findByChannelAndIsActiveTrue(ChannelType channel);
    
    /**
     * Find all active templates.
     */
    List<NotificationTemplate> findByIsActiveTrue();
    
    /**
     * Check if a template exists with the given name.
     * 
     * Useful for validation before creating a new template
     * (template names must be unique).
     */
    boolean existsByName(String name);
}
