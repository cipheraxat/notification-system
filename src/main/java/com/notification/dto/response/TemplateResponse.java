package com.notification.dto.response;

// =====================================================
// TemplateResponse.java - Template API Response
// =====================================================

import com.notification.model.entity.NotificationTemplate;
import com.notification.model.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for template data.
 * 
 * Example JSON response:
 * {
 *   "id": "abc123-...",
 *   "name": "welcome-email",
 *   "channel": "EMAIL",
 *   "subjectTemplate": "Welcome, {{userName}}!",
 *   "bodyTemplate": "Hi {{userName}}, thanks for joining...",
 *   "isActive": true,
 *   "createdAt": "2024-01-15T10:00:00Z",
 *   "updatedAt": "2024-01-15T10:00:00Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {

    private UUID id;
    private String name;
    private ChannelType channel;
    private String subjectTemplate;
    private String bodyTemplate;
    private boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    
    /**
     * Create a TemplateResponse from a NotificationTemplate entity.
     */
    public static TemplateResponse from(NotificationTemplate template) {
        if (template == null) {
            return null;
        }
        
        return TemplateResponse.builder()
            .id(template.getId())
            .name(template.getName())
            .channel(template.getChannel())
            .subjectTemplate(template.getSubjectTemplate())
            .bodyTemplate(template.getBodyTemplate())
            .isActive(template.isActive())
            .createdAt(template.getCreatedAt())
            .updatedAt(template.getUpdatedAt())
            .build();
    }
}
