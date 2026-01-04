package com.notification.scheduler;

// =====================================================
// RetryScheduler.java - Scheduled Retry Job
// =====================================================
//
// This scheduled job handles:
// 1. Processing notifications due for retry
// 2. Resetting stuck notifications
//
// Why do we need this?
// - If Kafka consumer fails, notifications stay in DB
// - Retry scheduling is based on time, not Kafka
// - Stuck notifications (PROCESSING for too long) need cleanup
//

import com.notification.model.entity.Notification;
import com.notification.model.enums.NotificationStatus;
import com.notification.repository.NotificationRepository;
import com.notification.service.channel.ChannelDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled job for processing notification retries.
 * 
 * Runs periodically to:
 * 1. Find notifications due for retry
 * 2. Process them through the channel handlers
 * 3. Reset stuck notifications
 */
@Component
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);
    
    private final NotificationRepository notificationRepository;
    private final ChannelDispatcher channelDispatcher;
    
    // How long before a PROCESSING notification is considered stuck
    @Value("${notification.retry.stuck-threshold-minutes:10}")
    private int stuckThresholdMinutes;
    
    // Batch size for processing
    @Value("${notification.retry.batch-size:100}")
    private int batchSize;
    
    public RetryScheduler(
            NotificationRepository notificationRepository,
            ChannelDispatcher channelDispatcher) {
        this.notificationRepository = notificationRepository;
        this.channelDispatcher = channelDispatcher;
    }
    
    /**
     * Process notifications due for retry.
     * 
     * @Scheduled makes this method run automatically.
     * 
     * fixedDelayString: Delay between end of last execution and start of next
     * initialDelayString: Delay before first execution after startup
     * 
     * Using String allows us to use SpEL expressions and config values.
     */
    @Scheduled(
        fixedDelayString = "${notification.retry.check-interval-ms:60000}",
        initialDelayString = "30000"  // Wait 30s after startup
    )
    @Transactional
    public void processRetries() {
        log.debug("Starting retry processing job...");
        
        OffsetDateTime now = OffsetDateTime.now();
        
        // Find notifications ready for processing (includes retries)
        List<Notification> notifications = notificationRepository
            .findReadyForProcessing(now, PageRequest.of(0, batchSize));
        
        if (notifications.isEmpty()) {
            log.debug("No notifications ready for processing");
            return;
        }
        
        log.info("Found {} notifications ready for processing", notifications.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (Notification notification : notifications) {
            try {
                // Skip if not in PENDING status (might have changed)
                if (notification.getStatus() != NotificationStatus.PENDING) {
                    continue;
                }
                
                // Mark as processing
                notification.markAsProcessing();
                notificationRepository.save(notification);
                
                // Attempt delivery
                boolean success = channelDispatcher.dispatch(notification);
                
                if (success) {
                    notification.markAsSent();
                    successCount++;
                    log.debug("Retry successful for notification {}", notification.getId());
                } else {
                    notification.scheduleRetry("Retry failed");
                    failCount++;
                    log.debug("Retry failed for notification {}, attempt {}/{}", 
                        notification.getId(), 
                        notification.getRetryCount(),
                        notification.getMaxRetries());
                }
                
                notificationRepository.save(notification);
                
            } catch (Exception e) {
                log.error("Error processing notification {}: {}", 
                    notification.getId(), e.getMessage());
                
                notification.scheduleRetry("Error: " + e.getMessage());
                notificationRepository.save(notification);
                failCount++;
            }
        }
        
        log.info("Retry processing complete: {} success, {} failed", successCount, failCount);
    }
    
    /**
     * Reset stuck notifications.
     * 
     * A notification is "stuck" if it's been in PROCESSING status
     * for too long (the worker might have crashed).
     * 
     * This runs less frequently than the retry job.
     */
    @Scheduled(
        fixedDelayString = "${notification.retry.stuck-check-interval-ms:300000}",  // 5 minutes
        initialDelayString = "60000"  // Wait 1 minute after startup
    )
    @Transactional
    public void resetStuckNotifications() {
        log.debug("Checking for stuck notifications...");
        
        OffsetDateTime cutoffTime = OffsetDateTime.now()
            .minusMinutes(stuckThresholdMinutes);
        
        int resetCount = notificationRepository.resetStuckNotifications(cutoffTime);
        
        if (resetCount > 0) {
            log.warn("Reset {} stuck notifications to PENDING status", resetCount);
        }
    }
    
    /**
     * Log statistics periodically (for monitoring).
     * 
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    @Transactional(readOnly = true)
    public void logStatistics() {
        long pending = notificationRepository.countByStatus(NotificationStatus.PENDING);
        long processing = notificationRepository.countByStatus(NotificationStatus.PROCESSING);
        long failed = notificationRepository.countByStatus(NotificationStatus.FAILED);
        long lastHour = notificationRepository.countByCreatedAtAfter(
            OffsetDateTime.now().minusHours(1)
        );
        
        log.info("Notification stats - Pending: {}, Processing: {}, Failed: {}, Last hour: {}",
            pending, processing, failed, lastHour);
    }
}
