package com.notification.model.entity;

// =====================================================
// NotificationTemplate.java - Reusable Message Templates
// =====================================================
//
// Templates allow you to define message formats once and
// reuse them with different data. This:
// - Ensures consistent messaging
// - Makes it easy to update messages
// - Supports multiple languages (future enhancement)
//
// Example:
//   Template: "Hi {{userName}}, your order #{{orderId}} is confirmed!"
//   Data: {userName: "John", orderId: "12345"}
//   Result: "Hi John, your order #12345 is confirmed!"
//

import com.notification.model.enums.ChannelType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * NotificationTemplate Entity - Stores reusable message templates.
 * 
 * Maps to the 'notification_templates' table.
 * 
 * Template Syntax:
 * - Use {{variableName}} for placeholders
 * - Example: "Hello {{userName}}, your code is {{otpCode}}"
 * 
 * Channel-Specific Notes:
 * - EMAIL: Has subject + body, can use HTML in body
 * - SMS: No subject, body must be short (~160 chars)
 * - PUSH: Has subject (title) + body, keep both short
 * - IN_APP: Has subject (title) + body
 */
@Entity
@Table(name = "notification_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    // ==================== Template Identification ====================
    
    /**
     * Unique name to identify this template.
     * 
     * Naming convention: {action}-{channel}
     * Examples:
     * - "welcome-email"
     * - "order-confirmation-sms"
     * - "password-reset-push"
     * 
     * This name is used in API requests:
     *   { "templateName": "welcome-email", "data": {...} }
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;
    
    /**
     * Which channel this template is for.
     * 
     * Templates are channel-specific because:
     * - EMAIL can be long and rich (HTML)
     * - SMS must be short (160 chars)
     * - PUSH needs a catchy title
     * - IN_APP can be anything
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;
    
    // ==================== Template Content ====================
    
    /**
     * Subject line template (for email and push notifications).
     * 
     * Can contain placeholders: "Order #{{orderId}} Confirmed!"
     * 
     * For SMS, this is usually null (SMS doesn't have a subject).
     */
    @Column(name = "subject_template", length = 500)
    private String subjectTemplate;
    
    /**
     * Message body template.
     * 
     * Can contain:
     * - Placeholders: {{userName}}, {{orderTotal}}
     * - For email: HTML tags for formatting
     * 
     * @Lob annotation is used for large text content.
     * In PostgreSQL, this maps to TEXT type.
     */
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;
    
    // ==================== Template Status ====================
    
    /**
     * Is this template active?
     * 
     * We use soft delete (isActive = false) instead of hard delete
     * because historical notifications reference this template.
     * 
     * Inactive templates:
     * - Cannot be used for new notifications
     * - Are preserved for audit/history
     * - Can be reactivated later
     */
    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;
    
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
    
    // ==================== Template Processing ====================
    
    /**
     * Process the subject template with provided variables.
     * 
     * Replaces {{placeholder}} with actual values from the map.
     * 
     * Example:
     *   Template: "Hi {{userName}}!"
     *   Variables: {"userName": "John"}
     *   Result: "Hi John!"
     * 
     * @param variables Map of placeholder names to values
     * @return Processed subject string, or null if no subject template
     */
    public String processSubject(java.util.Map<String, String> variables) {
        if (subjectTemplate == null) {
            return null;
        }
        return processTemplate(subjectTemplate, variables);
    }
    
    /**
     * Process the body template with provided variables.
     * 
     * @param variables Map of placeholder names to values
     * @return Processed body string
     */
    public String processBody(java.util.Map<String, String> variables) {
        return processTemplate(bodyTemplate, variables);
    }
    
    /**
     * Internal helper to replace placeholders in a template.
     * 
     * Uses simple regex to find {{name}} patterns and replace them.
     * 
     * @param template The template string with placeholders
     * @param variables Map of variable names to values
     * @return The processed string with placeholders replaced
     */
    private String processTemplate(String template, java.util.Map<String, String> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        
        String result = template;
        
        // Replace each variable in the template
        // {{variableName}} -> actual value
        for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        
        return result;
    }
}
