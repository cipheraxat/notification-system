package com.notification.service.channel;

// =====================================================
// SmsChannelHandler.java - SMS Delivery
// =====================================================
//
// Handles sending notifications via SMS.
//
// In a real system, this would integrate with:
// - Twilio
// - Nexmo/Vonage
// - AWS SNS
//
// SMS is the most expensive channel - use wisely!
//

import com.notification.model.entity.Notification;
import com.notification.model.entity.User;
import com.notification.model.enums.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SMS channel handler.
 * 
 * Sends notifications via SMS to users' phones.
 */
@Component
public class SmsChannelHandler implements ChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(SmsChannelHandler.class);
    
    // SMS character limit (standard SMS)
    private static final int SMS_MAX_LENGTH = 160;
    
    @Override
    public ChannelType getChannelType() {
        return ChannelType.SMS;
    }
    
    @Override
    public boolean canHandle(Notification notification) {
        if (!ChannelHandler.super.canHandle(notification)) {
            return false;
        }
        
        // Check if user has a phone number
        User user = notification.getUser();
        if (user == null || user.getPhone() == null || user.getPhone().isBlank()) {
            log.warn("Cannot send SMS: User {} has no phone number", 
                user != null ? user.getId() : "null");
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean send(Notification notification) {
        User user = notification.getUser();
        String phone = user.getPhone();
        
        // Truncate message if too long for SMS
        String message = notification.getContent();
        if (message.length() > SMS_MAX_LENGTH) {
            message = message.substring(0, SMS_MAX_LENGTH - 3) + "...";
            log.warn("SMS message truncated to {} characters", SMS_MAX_LENGTH);
        }
        
        log.info("========== SENDING SMS ==========");
        log.info("To: {}", phone);
        log.info("Message: {}", message);
        log.info("==================================");
        
        // =====================================================
        // TODO: Integrate with actual SMS provider
        // =====================================================
        //
        // Example with Twilio:
        //
        // Twilio.init(accountSid, authToken);
        // 
        // Message twilioMessage = Message.creator(
        //     new PhoneNumber(phone),      // To
        //     new PhoneNumber(fromPhone),  // From (your Twilio number)
        //     message                      // Body
        // ).create();
        // 
        // return twilioMessage.getStatus() == Message.Status.QUEUED ||
        //        twilioMessage.getStatus() == Message.Status.SENT;
        //
        
        // For demo: Simulate 90% success rate (SMS can fail more often)
        boolean success = Math.random() > 0.10;
        
        if (success) {
            log.info("SMS sent successfully to {}", phone);
        } else {
            log.warn("SMS failed to send to {} (simulated failure)", phone);
        }
        
        return success;
    }
}
