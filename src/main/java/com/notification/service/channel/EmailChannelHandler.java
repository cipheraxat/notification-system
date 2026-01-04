package com.notification.service.channel;

// =====================================================
// EmailChannelHandler.java - Email Delivery
// =====================================================
//
// Handles sending notifications via email.
//
// In a real system, this would integrate with:
// - SendGrid
// - Amazon SES
// - Mailgun
// - SMTP server
//
// For this demo, we just log the email (mock implementation).
//

import com.notification.model.entity.Notification;
import com.notification.model.entity.User;
import com.notification.model.enums.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Email channel handler.
 * 
 * Sends notifications via email to users.
 */
@Component
public class EmailChannelHandler implements ChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelHandler.class);
    
    @Override
    public ChannelType getChannelType() {
        return ChannelType.EMAIL;
    }
    
    @Override
    public boolean canHandle(Notification notification) {
        if (!ChannelHandler.super.canHandle(notification)) {
            return false;
        }
        
        // Check if user has an email address
        User user = notification.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send email: User {} has no email address", 
                user != null ? user.getId() : "null");
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean send(Notification notification) {
        User user = notification.getUser();
        String email = user.getEmail();
        
        log.info("========== SENDING EMAIL ==========");
        log.info("To: {}", email);
        log.info("Subject: {}", notification.getSubject());
        log.info("Body: {}", notification.getContent());
        log.info("====================================");
        
        // =====================================================
        // TODO: Integrate with actual email provider
        // =====================================================
        // 
        // Example with SendGrid:
        //
        // Email from = new Email("noreply@yourapp.com");
        // Email to = new Email(email);
        // Content content = new Content("text/html", notification.getContent());
        // Mail mail = new Mail(from, notification.getSubject(), to, content);
        // 
        // SendGrid sg = new SendGrid(sendGridApiKey);
        // Request request = new Request();
        // request.setMethod(Method.POST);
        // request.setEndpoint("mail/send");
        // request.setBody(mail.build());
        // 
        // Response response = sg.api(request);
        // return response.getStatusCode() >= 200 && response.getStatusCode() < 300;
        //
        
        // For demo: Simulate 95% success rate
        boolean success = Math.random() > 0.05;
        
        if (success) {
            log.info("Email sent successfully to {}", email);
        } else {
            log.warn("Email failed to send to {} (simulated failure)", email);
        }
        
        return success;
    }
}
