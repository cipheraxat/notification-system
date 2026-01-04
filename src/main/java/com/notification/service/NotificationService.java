package com.notification.service;

// =====================================================
// NotificationService.java - Core Business Logic
// =====================================================
//
// This is the HEART of our notification system!
// It orchestrates the entire notification flow:
// 1. Validate request
// 2. Check rate limits
// 3. Process template (if using one)
// 4. Create notification record
// 5. Send to Kafka for async processing
//

import com.notification.dto.request.BulkNotificationRequest;
import com.notification.dto.request.SendNotificationRequest;
import com.notification.dto.response.BulkNotificationResponse;
import com.notification.dto.response.NotificationResponse;
import com.notification.dto.response.PagedResponse;
import com.notification.exception.NotificationException;
import com.notification.exception.ResourceNotFoundException;
import com.notification.model.entity.Notification;
import com.notification.model.entity.User;
import com.notification.model.enums.ChannelType;
import com.notification.model.enums.NotificationStatus;
import com.notification.repository.NotificationRepository;
import com.notification.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Main service for notification operations.
 * 
 * Handles:
 * - Sending single notifications
 * - Sending bulk notifications
 * - Retrieving user's notification inbox
 * - Marking notifications as read
 * - Analytics and status queries
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    
    // ==================== Dependencies ====================
    
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TemplateService templateService;
    private final RateLimiterService rateLimiterService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    // Kafka topic name from configuration
    @Value("${notification.kafka.topic:notifications}")
    private String notificationTopic;
    
    // ==================== Constructor ====================
    
    /**
     * Constructor injection of all dependencies.
     * 
     * Spring will automatically create and inject these beans.
     * This is called "Dependency Injection" and makes testing easier
     * (we can pass mock objects in tests).
     */
    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            TemplateService templateService,
            RateLimiterService rateLimiterService,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.templateService = templateService;
        this.rateLimiterService = rateLimiterService;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    // ==================== Send Notification ====================
    
    /**
     * Send a single notification.
     * 
     * This is the main entry point for sending notifications.
     * 
     * Flow:
     * 1. Validate user exists
     * 2. Check rate limit
     * 3. Process template OR use direct content
     * 4. Create notification record in DB
     * 5. Send to Kafka for async delivery
     * 
     * @param request The notification request
     * @return The created notification response
     */
    @Transactional
    public NotificationResponse sendNotification(SendNotificationRequest request) {
        log.info("Processing notification request for user: {}", request.getUserId());
        
        // Step 1: Validate user exists
        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));
        
        // Step 2: Check rate limit (throws exception if exceeded)
        rateLimiterService.checkAndIncrement(user.getId(), request.getChannel());
        
        // Step 3: Get content (from template or direct)
        String subject;
        String content;
        
        if (request.isTemplateRequest()) {
            // Using a template - process it with variables
            TemplateService.ProcessedTemplate processed = 
                templateService.processTemplate(
                    request.getTemplateName(), 
                    request.getTemplateVariables()
                );
            subject = processed.getSubject();
            content = processed.getBody();
            
            // Validate channel matches template
            if (processed.getChannel() != request.getChannel()) {
                throw new NotificationException(
                    "Template '" + request.getTemplateName() + 
                    "' is for channel " + processed.getChannel() + 
                    ", not " + request.getChannel()
                );
            }
        } else {
            // Direct content
            if (!request.hasValidContent()) {
                throw new IllegalArgumentException(
                    "Either templateName or content must be provided"
                );
            }
            subject = request.getSubject();
            content = request.getContent();
        }
        
        // Step 4: Create notification record
        Notification notification = Notification.builder()
            .user(user)
            .channel(request.getChannel())
            .priority(request.getPriority())
            .subject(subject)
            .content(content)
            .status(NotificationStatus.PENDING)
            .build();
        
        notification = notificationRepository.save(notification);
        
        log.info("Created notification {} for user {}", notification.getId(), user.getId());
        
        // Step 5: Send to Kafka for async processing
        sendToKafka(notification);
        
        return NotificationResponse.from(notification);
    }
    
    /**
     * Send bulk notifications to multiple users.
     * 
     * For each user, we:
     * 1. Check rate limit
     * 2. Create notification
     * 3. Send to Kafka
     * 
     * Failed notifications don't stop the whole batch.
     */
    @Transactional
    public BulkNotificationResponse sendBulkNotification(BulkNotificationRequest request) {
        log.info("Processing bulk notification for {} users", request.getUserIds().size());
        
        BulkNotificationResponse response = BulkNotificationResponse.builder()
            .totalRequested(request.getUserIds().size())
            .build();
        
        // Process content once (same for all users)
        String subject;
        String content;
        
        if (request.isTemplateRequest()) {
            TemplateService.ProcessedTemplate processed = 
                templateService.processTemplate(
                    request.getTemplateName(), 
                    request.getTemplateVariables()
                );
            subject = processed.getSubject();
            content = processed.getBody();
        } else {
            subject = request.getSubject();
            content = request.getContent();
        }
        
        // Process each user
        for (UUID userId : request.getUserIds()) {
            try {
                // Find user
                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                
                // Check rate limit (skip if exceeded, don't fail whole batch)
                if (rateLimiterService.isRateLimited(userId, request.getChannel())) {
                    response.addFailure(userId, "Rate limit exceeded");
                    continue;
                }
                
                // Increment rate limit counter
                rateLimiterService.checkAndIncrement(userId, request.getChannel());
                
                // Create notification
                Notification notification = Notification.builder()
                    .user(user)
                    .channel(request.getChannel())
                    .priority(request.getPriority())
                    .subject(subject)
                    .content(content)
                    .status(NotificationStatus.PENDING)
                    .build();
                
                notification = notificationRepository.save(notification);
                
                // Send to Kafka
                sendToKafka(notification);
                
                response.addSuccess(notification.getId());
                
            } catch (Exception e) {
                log.error("Failed to create notification for user {}: {}", 
                    userId, e.getMessage());
                response.addFailure(userId, e.getMessage());
            }
        }
        
        log.info("Bulk notification complete: {} success, {} failed", 
            response.getSuccessCount(), response.getFailedCount());
        
        return response;
    }
    
    // ==================== Query Methods ====================
    
    /**
     * Get a notification by ID.
     */
    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(UUID id) {
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
        
        return NotificationResponse.from(notification);
    }
    
    /**
     * Get notifications for a user (inbox).
     * 
     * Supports pagination for large inboxes.
     */
    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getUserNotifications(
            UUID userId, 
            Pageable pageable) {
        
        // Verify user exists
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        
        Page<Notification> page = notificationRepository
            .findByUserIdOrderByCreatedAtDesc(userId, pageable);
        
        return PagedResponse.from(page, NotificationResponse::from);
    }
    
    /**
     * Get notifications for a user filtered by channel.
     */
    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getUserNotificationsByChannel(
            UUID userId, 
            ChannelType channel, 
            Pageable pageable) {
        
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        
        Page<Notification> page = notificationRepository
            .findByUserIdAndChannelOrderByCreatedAtDesc(userId, channel, pageable);
        
        return PagedResponse.from(page, NotificationResponse::from);
    }
    
    /**
     * Get unread count for a user (in-app notifications).
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countUnreadForUser(userId);
    }
    
    // ==================== Status Update Methods ====================
    
    /**
     * Mark a notification as read.
     */
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        
        if (notification.getChannel() != ChannelType.IN_APP) {
            throw new IllegalArgumentException(
                "Only IN_APP notifications can be marked as read"
            );
        }
        
        notification.markAsRead();
        notification = notificationRepository.save(notification);
        
        return NotificationResponse.from(notification);
    }
    
    /**
     * Mark all notifications as read for a user.
     */
    @Transactional
    public int markAllAsRead(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        
        return notificationRepository.markAllAsReadForUser(userId, OffsetDateTime.now());
    }
    
    // ==================== Private Helper Methods ====================
    
    /**
     * Send notification to Kafka for async processing.
     * 
     * We serialize just the notification ID - the worker will
     * fetch the full notification from the database.
     * 
     * Key = notification ID (for partitioning)
     * Value = notification ID (worker will fetch details)
     */
    private void sendToKafka(Notification notification) {
        try {
            String key = notification.getId().toString();
            String value = notification.getId().toString();
            
            kafkaTemplate.send(notificationTopic, key, value);
            
            log.debug("Sent notification {} to Kafka topic {}", 
                notification.getId(), notificationTopic);
                
        } catch (Exception e) {
            log.error("Failed to send notification {} to Kafka: {}", 
                notification.getId(), e.getMessage());
            // Don't fail the transaction - notification is saved in DB
            // A retry job can pick it up later
        }
    }
}
