package com.notification.service;

// =====================================================
// TemplateService.java - Template Management
// =====================================================
//
// Handles CRUD operations for notification templates
// and template processing (variable substitution).
//

import com.notification.dto.request.CreateTemplateRequest;
import com.notification.dto.response.TemplateResponse;
import com.notification.exception.ResourceNotFoundException;
import com.notification.model.entity.NotificationTemplate;
import com.notification.model.enums.ChannelType;
import com.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing notification templates.
 * 
 * Templates allow reusable message formats with placeholders
 * that get replaced with actual values at send time.
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);
    
    private final NotificationTemplateRepository templateRepository;
    
    public TemplateService(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }
    
    // ==================== Template CRUD Operations ====================
    
    /**
     * Create a new notification template.
     * 
     * @Transactional ensures that if anything fails, changes are rolled back.
     * The default is readOnly=false, which allows write operations.
     * 
     * @CacheEvict clears all cached templates when a new one is created.
     * This ensures cache consistency - we don't want stale cached data.
     * 
     * @param request The template creation request
     * @return The created template as a response DTO
     */
    @Transactional
    @CacheEvict(value = "templates", allEntries = true)
    public TemplateResponse createTemplate(CreateTemplateRequest request) {
        log.info("Creating template: {}", request.getName());
        
        // Check if template name already exists
        if (templateRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException(
                "Template with name '" + request.getName() + "' already exists"
            );
        }
        
        // Build the entity from request
        NotificationTemplate template = NotificationTemplate.builder()
            .name(request.getName())
            .channel(request.getChannel())
            .subjectTemplate(request.getSubjectTemplate())
            .bodyTemplate(request.getBodyTemplate())
            .isActive(true)
            .build();
        
        // Save to database
        template = templateRepository.save(template);
        
        log.info("Created template with ID: {}", template.getId());
        
        // Convert to response DTO
        return TemplateResponse.from(template);
    }
    
    /**
     * Get a template by ID.
     * 
     * @Transactional(readOnly = true) optimizes for read-only operations.
     * It tells the database we won't modify anything, allowing optimizations.
     */
    @Transactional(readOnly = true)
    public TemplateResponse getTemplateById(UUID id) {
        NotificationTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Template", "id", id));
        
        return TemplateResponse.from(template);
    }
    
    /**
     * Get a template by name.
     * 
     * @Cacheable caches the result to avoid repeated database lookups.
     * Templates are read frequently but updated rarely.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "templates", key = "'name:' + #name")
    public TemplateResponse getTemplateByName(String name) {
        NotificationTemplate template = templateRepository.findByName(name)
            .orElseThrow(() -> new ResourceNotFoundException("Template", "name", name));
        
        return TemplateResponse.from(template);
    }
    
    /**
     * Get all templates, optionally filtered by channel.
     */
    @Transactional(readOnly = true)
    public List<TemplateResponse> getAllTemplates(ChannelType channel) {
        List<NotificationTemplate> templates;
        
        if (channel != null) {
            templates = templateRepository.findByChannelAndIsActiveTrue(channel);
        } else {
            templates = templateRepository.findByIsActiveTrue();
        }
        
        // Convert list of entities to list of DTOs
        // Using Stream API for clean, functional-style code
        return templates.stream()
            .map(TemplateResponse::from)  // Method reference
            .collect(Collectors.toList());
    }
    
    /**
     * Update a template.
     * 
     * @CacheEvict clears all cached templates when one is updated.
     * This ensures cache consistency - updated templates must be refreshed.
     */
    @Transactional
    @CacheEvict(value = "templates", allEntries = true)
    public TemplateResponse updateTemplate(UUID id, CreateTemplateRequest request) {
        NotificationTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Template", "id", id));
        
        // Check if new name conflicts with existing template
        if (!template.getName().equals(request.getName()) && 
            templateRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException(
                "Template with name '" + request.getName() + "' already exists"
            );
        }
        
        // Update fields
        template.setName(request.getName());
        template.setChannel(request.getChannel());
        template.setSubjectTemplate(request.getSubjectTemplate());
        template.setBodyTemplate(request.getBodyTemplate());
        
        template = templateRepository.save(template);
        
        log.info("Updated template: {}", id);
        
        return TemplateResponse.from(template);
    }
    
    /**
     * Soft delete a template (mark as inactive).
     * 
     * We don't hard delete because historical notifications
     * reference this template for audit purposes.
     * 
     * @CacheEvict clears all cached templates when one is deleted.
     * This ensures cache consistency - deleted templates are removed from cache.
     */
    @Transactional
    @CacheEvict(value = "templates", allEntries = true)
    public void deleteTemplate(UUID id) {
        NotificationTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Template", "id", id));
        
        template.setActive(false);
        templateRepository.save(template);
        
        log.info("Deactivated template: {}", id);
    }
    
    // ==================== Template Processing ====================
    
    /**
     * Process a template with variables to get the final content.
     * 
     * This is used internally by NotificationService when sending
     * template-based notifications.
     * 
     * @param templateName The name of the template
     * @param variables Map of placeholder names to values
     * @return ProcessedTemplate with subject and body filled in
     */
    @Transactional(readOnly = true)
    public ProcessedTemplate processTemplate(String templateName, Map<String, String> variables) {
        // Find the template (must be active)
        NotificationTemplate template = templateRepository.findByNameAndIsActiveTrue(templateName)
            .orElseThrow(() -> new ResourceNotFoundException("Template", "name", templateName));
        
        // Process subject and body with variables
        String processedSubject = template.processSubject(variables);
        String processedBody = template.processBody(variables);
        
        return new ProcessedTemplate(
            template.getChannel(),
            processedSubject,
            processedBody
        );
    }
    
    /**
     * Inner class to hold processed template content.
     * 
     * This is a simple data holder (could also use a record in Java 16+).
     */
    public static class ProcessedTemplate {
        private final ChannelType channel;
        private final String subject;
        private final String body;
        
        public ProcessedTemplate(ChannelType channel, String subject, String body) {
            this.channel = channel;
            this.subject = subject;
            this.body = body;
        }
        
        public ChannelType getChannel() { return channel; }
        public String getSubject() { return subject; }
        public String getBody() { return body; }
    }
}
