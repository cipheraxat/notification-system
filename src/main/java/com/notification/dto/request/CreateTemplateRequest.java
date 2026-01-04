package com.notification.dto.request;

// =====================================================
// CreateTemplateRequest.java - Template Creation Request
// =====================================================
//
// Request body for creating a new notification template.
//

import com.notification.model.enums.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a notification template.
 * 
 * Example JSON request:
 * {
 *   "name": "order-shipped",
 *   "channel": "EMAIL",
 *   "subjectTemplate": "Your Order #{{orderId}} Has Shipped!",
 *   "bodyTemplate": "Hi {{userName}},\n\nYour order is on its way!\n\nTracking: {{trackingUrl}}"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateRequest {

    /**
     * Unique name for this template.
     * 
     * @NotBlank ensures the string is:
     * - Not null
     * - Not empty ("")
     * - Not just whitespace ("   ")
     * 
     * Naming convention: {action}-{channel}
     * Examples: "welcome-email", "otp-sms", "order-push"
     */
    @NotBlank(message = "Template name is required")
    private String name;
    
    /**
     * Which channel this template is for.
     */
    @NotNull(message = "Channel is required")
    private ChannelType channel;
    
    /**
     * Subject line template (optional for SMS).
     * 
     * Can contain placeholders: "Order #{{orderId}} Confirmed"
     */
    private String subjectTemplate;
    
    /**
     * Message body template.
     * 
     * Can contain placeholders: "Hi {{userName}}, ..."
     */
    @NotBlank(message = "Body template is required")
    private String bodyTemplate;
}
