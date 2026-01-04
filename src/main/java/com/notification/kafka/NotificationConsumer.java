package com.notification.kafka;

// =====================================================
// NotificationConsumer.java - Kafka Message Consumer
// =====================================================
//
// This is the WORKER that processes notifications asynchronously.
//
// Flow:
// 1. NotificationService saves notification to DB and sends ID to Kafka
// 2. Kafka stores the message in a partition
// 3. This consumer picks up the message
// 4. We fetch the notification from DB
// 5. We send it via the appropriate channel (email, SMS, etc.)
// 6. We update the status in DB
//
// Why Kafka?
// - Decouples sending from API response (fast API, slow sending)
// - Handles backpressure (too many notifications at once)
// - Provides durability (messages survive restarts)
// - Enables horizontal scaling (add more consumers)
//

import com.notification.model.entity.Notification;
import com.notification.model.enums.NotificationStatus;
import com.notification.repository.NotificationRepository;
import com.notification.service.channel.ChannelDispatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Kafka consumer for processing notifications.
 * 
 * @Component makes this a Spring bean that gets auto-detected.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    
    private final NotificationRepository notificationRepository;
    private final ChannelDispatcher channelDispatcher;
    
    public NotificationConsumer(
            NotificationRepository notificationRepository,
            ChannelDispatcher channelDispatcher) {
        this.notificationRepository = notificationRepository;
        this.channelDispatcher = channelDispatcher;
    }
    
    /**
     * Listen for notification messages from Kafka.
     * 
     * @KafkaListener tells Spring Kafka to:
     * 1. Subscribe to the specified topic
     * 2. Call this method for each message
     * 3. Handle deserialization automatically
     * 
     * Parameters:
     * - topics: The topic(s) to listen to
     * - groupId: Consumer group ID (allows multiple instances to share work)
     * - containerFactory: The listener container factory to use
     * 
     * ConsumerRecord contains:
     * - key(): The message key (notification ID)
     * - value(): The message value (also notification ID in our case)
     * - partition(): Which partition the message came from
     * - offset(): The message's offset in the partition
     * 
     * Acknowledgment allows manual commit of offsets.
     * We only acknowledge after successful processing.
     */
    @KafkaListener(
        topics = "${notification.kafka.topic:notifications}",
        groupId = "${spring.kafka.consumer.group-id:notification-service}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void processNotification(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        String notificationIdStr = record.value();
        
        log.info("Received notification from Kafka: {} (partition={}, offset={})",
            notificationIdStr, record.partition(), record.offset());
        
        try {
            // Parse the notification ID
            UUID notificationId = UUID.fromString(notificationIdStr);
            
            // Fetch the notification from database
            Optional<Notification> optNotification = notificationRepository.findById(notificationId);
            
            if (optNotification.isEmpty()) {
                log.warn("Notification not found: {}. Might have been deleted.", notificationId);
                acknowledgment.acknowledge();
                return;
            }
            
            Notification notification = optNotification.get();
            
            // Skip if already processed
            if (notification.getStatus() != NotificationStatus.PENDING) {
                log.info("Notification {} already processed (status={}). Skipping.",
                    notificationId, notification.getStatus());
                acknowledgment.acknowledge();
                return;
            }
            
            // Mark as processing
            notification.markAsProcessing();
            notificationRepository.save(notification);
            
            // Dispatch to the appropriate channel handler
            boolean success = channelDispatcher.dispatch(notification);
            
            if (success) {
                // Mark as sent
                notification.markAsSent();
                notificationRepository.save(notification);
                log.info("Successfully sent notification: {}", notificationId);
            } else {
                // Schedule retry
                notification.scheduleRetry("Delivery failed");
                notificationRepository.save(notification);
                log.warn("Notification {} failed. Scheduled retry #{}", 
                    notificationId, notification.getRetryCount());
            }
            
            // Acknowledge the message (commit offset)
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing notification {}: {}", notificationIdStr, e.getMessage(), e);
            
            // Try to update the notification status
            try {
                UUID notificationId = UUID.fromString(notificationIdStr);
                notificationRepository.findById(notificationId).ifPresent(notification -> {
                    notification.scheduleRetry("Processing error: " + e.getMessage());
                    notificationRepository.save(notification);
                });
            } catch (Exception ex) {
                log.error("Failed to update notification status: {}", ex.getMessage());
            }
            
            // Acknowledge to avoid infinite retry loop
            // The notification will be retried via the scheduled retry job
            acknowledgment.acknowledge();
        }
    }
}
