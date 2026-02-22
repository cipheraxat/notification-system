package com.notification.service;

// =====================================================
// NotificationServiceTest.java - Unit Tests
// =====================================================
//
// Unit tests for the NotificationService.
// 
// We use Mockito to mock dependencies so we can test
// the service logic in isolation.
//

import com.notification.dto.request.SendNotificationRequest;
import com.notification.dto.response.NotificationResponse;
import com.notification.exception.ResourceNotFoundException;
import com.notification.model.entity.Notification;
import com.notification.model.entity.User;
import com.notification.model.enums.ChannelType;
import com.notification.model.enums.NotificationStatus;
import com.notification.model.enums.Priority;
import com.notification.repository.NotificationRepository;
import com.notification.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService.
 * 
 * @ExtendWith(MockitoExtension.class) enables Mockito annotations.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    // @Mock creates a mock object
    @Mock
    private NotificationRepository notificationRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private UserService userService;
    
    @Mock
    private TemplateService templateService;
    
    @Mock
    private RateLimiterService rateLimiterService;
    
    @Mock
    private DeduplicationService deduplicationService;
    
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    
    // @InjectMocks creates the service and injects all mocks
    @InjectMocks
    private NotificationService notificationService;
    
    // Test data
    private User testUser;
    private UUID testUserId;
    
    /**
     * Set up test data before each test.
     */
    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = User.builder()
            .id(testUserId)
            .email("test@example.com")
            .phone("+1234567890")
            .build();
    }
    
    // ==================== Send Notification Tests ====================
    
    @Test
    @DisplayName("Should send notification with direct content")
    void sendNotification_WithDirectContent_Success() {
        // Arrange (set up test data and mock behavior)
        SendNotificationRequest request = SendNotificationRequest.builder()
            .userId(testUserId)
            .channel(ChannelType.EMAIL)
            .priority(Priority.HIGH)
            .subject("Test Subject")
            .content("Test Content")
            .build();
        
        // Mock userService to return our test user (cached lookup)
        when(userService.findById(testUserId)).thenReturn(testUser);
        
        // Mock rate limiter to allow the notification
        when(rateLimiterService.checkAndIncrement(any(), any())).thenReturn(true);
        
        // Mock repository save to return the notification with an ID
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> {
                Notification n = invocation.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });
        
        // Act (call the method being tested)
        NotificationResponse response = notificationService.sendNotification(request);
        
        // Assert (verify the results)
        assertNotNull(response);
        assertEquals(testUserId, response.getUserId());
        assertEquals(ChannelType.EMAIL, response.getChannel());
        assertEquals(Priority.HIGH, response.getPriority());
        assertEquals("Test Subject", response.getSubject());
        assertEquals("Test Content", response.getContent());
        assertEquals(NotificationStatus.PENDING, response.getStatus());
        
        // Verify interactions with mocks
        verify(userService).findById(testUserId);
        verify(rateLimiterService).checkAndIncrement(testUserId, ChannelType.EMAIL);
        verify(notificationRepository).save(any(Notification.class));
    }
    
    @Test
    @DisplayName("Should throw exception when user not found")
    void sendNotification_UserNotFound_ThrowsException() {
        // Arrange
        SendNotificationRequest request = SendNotificationRequest.builder()
            .userId(testUserId)
            .channel(ChannelType.EMAIL)
            .content("Test Content")
            .build();
        
        // Mock user service to throw (user not found)
        when(userService.findById(testUserId)).thenThrow(
            new ResourceNotFoundException("User not found with id: " + testUserId));
        
        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> notificationService.sendNotification(request)
        );
        
        assertTrue(exception.getMessage().contains("User not found"));
        
        // Verify no notification was saved
        verify(notificationRepository, never()).save(any());
    }
    
    @Test
    @DisplayName("Should throw exception when no content provided")
    void sendNotification_NoContent_ThrowsException() {
        // Arrange
        SendNotificationRequest request = SendNotificationRequest.builder()
            .userId(testUserId)
            .channel(ChannelType.EMAIL)
            // No content or template
            .build();
        
        when(userService.findById(testUserId)).thenReturn(testUser);
        when(rateLimiterService.checkAndIncrement(any(), any())).thenReturn(true);
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> notificationService.sendNotification(request)
        );
        
        assertTrue(exception.getMessage().contains("templateName or content"));
    }
    
    // ==================== Get Notification Tests ====================
    
    @Test
    @DisplayName("Should get notification by ID")
    void getNotificationById_Found_ReturnsNotification() {
        // Arrange
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
            .id(notificationId)
            .user(testUser)
            .channel(ChannelType.EMAIL)
            .content("Test")
            .status(NotificationStatus.SENT)
            .build();
        
        when(notificationRepository.findById(notificationId))
            .thenReturn(Optional.of(notification));
        
        // Act
        NotificationResponse response = notificationService.getNotificationById(notificationId);
        
        // Assert
        assertNotNull(response);
        assertEquals(notificationId, response.getId());
        assertEquals(NotificationStatus.SENT, response.getStatus());
    }
    
    @Test
    @DisplayName("Should return duplicate response when eventId is already seen")
    void sendNotification_WithDuplicateEventId_ReturnsDuplicateResponse() {
        // Arrange
        String eventId = "duplicate-event-123";
        SendNotificationRequest request = SendNotificationRequest.builder()
            .userId(testUserId)
            .channel(ChannelType.EMAIL)
            .eventId(eventId)
            .subject("Test Subject")
            .content("Test Content")
            .build();
        
        // Mock repository and services
        when(userService.findById(testUserId)).thenReturn(testUser);
        when(deduplicationService.isDuplicate(eventId)).thenReturn(true);
        
        // Act
        NotificationResponse response = notificationService.sendNotification(request);
        
        // Assert
        assertNotNull(response);
        assertNull(response.getId()); // No notification created
        assertEquals(testUserId, response.getUserId());
        assertEquals(ChannelType.EMAIL, response.getChannel());
        assertEquals(NotificationStatus.FAILED, response.getStatus());
        assertEquals("Duplicate event: notification already processed", response.getErrorMessage());
        
        // Verify that notification was not saved and Kafka was not called
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }
    
    @Test
    @DisplayName("Should throw exception when notification not found")
    void getNotificationById_NotFound_ThrowsException() {
        // Arrange
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(
            ResourceNotFoundException.class,
            () -> notificationService.getNotificationById(notificationId)
        );
    }
}
