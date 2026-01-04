package com.notification.service.channel;

// =====================================================
// InAppChannelHandler.java - In-App Notification Delivery
// =====================================================
//
// In-app notifications are stored in our database and
// displayed in the user's notification inbox within the app.
//
// This is the simplest channel because:
// - No external provider needed
// - Already saved to database
// - High reliability (no external failures)
//
// The notification is already saved to the database before
// this handler runs, so we just need to mark it as delivered.
//

import com.notification.model.entity.Notification;
import com.notification.model.enums.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-app notification channel handler.
 * 
 * Marks notifications as delivered for the in-app inbox.
 */
@Component
public class InAppChannelHandler implements ChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(InAppChannelHandler.class);
    
    @Override
    public ChannelType getChannelType() {
        return ChannelType.IN_APP;
    }
    
    @Override
    public boolean canHandle(Notification notification) {
        // In-app notifications just need a valid user
        return ChannelHandler.super.canHandle(notification) &&
               notification.getUser() != null;
    }
    
    @Override
    public boolean send(Notification notification) {
        log.info("========== IN-APP NOTIFICATION ==========");
        log.info("User ID: {}", notification.getUser().getId());
        log.info("Subject: {}", notification.getSubject());
        log.info("Content: {}", notification.getContent());
        log.info("==========================================");
        
        // In-app notifications are already stored in the database.
        // The "send" here just means marking it as ready for the user's inbox.
        // 
        // In a real-time system, you might also:
        // 1. Send a WebSocket message to the user's browser/app
        // 2. Update a real-time cache
        // 3. Trigger a client-side notification
        
        // =====================================================
        // TODO: Optional real-time delivery via WebSocket
        // =====================================================
        //
        // Example with Spring WebSocket:
        //
        // String userId = notification.getUser().getId().toString();
        // String destination = "/user/" + userId + "/queue/notifications";
        // 
        // NotificationResponse dto = NotificationResponse.from(notification);
        // messagingTemplate.convertAndSend(destination, dto);
        //
        
        // In-app always succeeds (already in database)
        log.info("In-app notification delivered to user {}", notification.getUser().getId());
        return true;
    }
}
