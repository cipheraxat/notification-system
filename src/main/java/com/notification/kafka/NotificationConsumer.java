package com.notification.kafka;

// =====================================================
// NotificationConsumer.java - Kafka Message Consumer
// =====================================================
//
// This is the WORKER that processes notifications asynchronously.
//
// Architecture (Alex Xu's Design):
// - Each channel has its own Kafka topic for independent scaling
// - Topics: notifications.email, notifications.sms, notifications.push, notifications.in-app
// - This allows scaling email consumers independently from SMS consumers
//
// Flow:
// 1. NotificationService saves notification to DB and sends ID to channel-specific Kafka topic
// 2. Kafka stores the message in a partition
// 3. This consumer picks up the message from the appropriate topic
// 4. We fetch the notification from DB
// 5. We send it via the appropriate channel (email, SMS, etc.)
// 6. We update the status in DB
//
// Why Channel-Specific Topics?
// - Independent scaling (email may need 10 consumers, SMS only 2)
// - Isolation (email issues don't affect push delivery)
// - Different processing priorities
// - Easier monitoring per channel
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
 * Kafka consumer for processing notifications from channel-specific topics.
 * 
 * Listens to all four channel topics:
 * - notifications.email
 * - notifications.sms
 * - notifications.push
 * - notifications.in-app
 * 
 * Each topic can be scaled independently by adjusting concurrency.
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
    
    // ==================== Channel-Specific Consumers ====================
    // 
    // Each channel has its own listener for independent scaling.
    // In production, you can adjust concurrency per channel based on volume.
    //
    
    /**
     * Process EMAIL notifications.
     * 
     * Email is typically high volume but can tolerate some delay.
     * Consider higher concurrency for production.
     */
    @KafkaListener(
        topics = "${notification.kafka.topic.email:notifications.email}",
        groupId = "${spring.kafka.consumer.group-id:notification-service}-email",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void processEmailNotification(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        processNotification(record, acknowledgment, "EMAIL");
    }
    
    /**
     * Process SMS notifications.
     * 
     * SMS is expensive and rate-limited by providers.
     * Lower concurrency to respect external rate limits.
     */
    @KafkaListener(
        topics = "${notification.kafka.topic.sms:notifications.sms}",
        groupId = "${spring.kafka.consumer.group-id:notification-service}-sms",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void processSmsNotification(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        processNotification(record, acknowledgment, "SMS");
    }
    
    /**
     * Process PUSH notifications.
     * 
     * Push notifications need to be fast for user experience.
     * Consider higher concurrency for production.
     */
    @KafkaListener(
        topics = "${notification.kafka.topic.push:notifications.push}",
        groupId = "${spring.kafka.consumer.group-id:notification-service}-push",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void processPushNotification(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        processNotification(record, acknowledgment, "PUSH");
    }
    
    /**
     * Process IN-APP notifications.
     * 
     * In-app notifications are stored locally, very fast.
     * Moderate concurrency is usually sufficient.
     */
    @KafkaListener(
        topics = "${notification.kafka.topic.in-app:notifications.in-app}",
        groupId = "${spring.kafka.consumer.group-id:notification-service}-inapp",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void processInAppNotification(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        processNotification(record, acknowledgment, "IN_APP");
    }
    
    // ==================== Common Processing Logic ====================
    
    /**
     * Common notification processing logic for all channels.
     * 
     * @param record The Kafka record containing notification ID
     * @param acknowledgment Manual acknowledgment handle
     * @param channel The channel type for logging
     */
    private void processNotification(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment,
            String channel) {
        
        String notificationIdStr = record.value();
        
        // Clean the string - remove any surrounding quotes or whitespace
        notificationIdStr = notificationIdStr.trim().replaceAll("^\"|\"$", "");
        
        log.info("Received {} notification from Kafka: {} (topic={}, partition={}, offset={})",
            channel, notificationIdStr, record.topic(), record.partition(), record.offset());
        
        try {
            // Parse the notification ID
            UUID notificationId = UUID.fromString(notificationIdStr);
            
            // Fetch the notification from database with user eagerly loaded
            Optional<Notification> optNotification = notificationRepository.findByIdWithUser(notificationId);
            
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
