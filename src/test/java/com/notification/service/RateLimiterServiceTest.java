package com.notification.service;

// =====================================================
// RateLimiterServiceTest.java - Rate Limiter Tests
// =====================================================

import com.notification.exception.RateLimitExceededException;
import com.notification.model.enums.ChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimiterService.
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    private RateLimiterService rateLimiterService;
    
    private UUID testUserId;
    
    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        
        // Create service
        rateLimiterService = new RateLimiterService(redisTemplate);
        
        // Set rate limits using reflection (normally set by @Value)
        ReflectionTestUtils.setField(rateLimiterService, "emailLimit", 10);
        ReflectionTestUtils.setField(rateLimiterService, "smsLimit", 5);
        ReflectionTestUtils.setField(rateLimiterService, "pushLimit", 20);
        ReflectionTestUtils.setField(rateLimiterService, "inAppLimit", 100);
        
        // Mock Redis operations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
    
    @Test
    @DisplayName("Should allow notification when under limit")
    void checkAndIncrement_UnderLimit_ReturnsTrue() {
        // Arrange - user has sent 5 emails, limit is 10
        when(valueOperations.get(anyString())).thenReturn("5");
        when(valueOperations.increment(anyString())).thenReturn(6L);
        
        // Act
        boolean result = rateLimiterService.checkAndIncrement(testUserId, ChannelType.EMAIL);
        
        // Assert
        assertTrue(result);
        verify(valueOperations).increment(anyString());
    }
    
    @Test
    @DisplayName("Should throw exception when limit exceeded")
    void checkAndIncrement_LimitExceeded_ThrowsException() {
        // Arrange - user has sent 10 emails, limit is 10
        when(valueOperations.get(anyString())).thenReturn("10");
        when(redisTemplate.getExpire(anyString(), any())).thenReturn(1800L); // 30 min TTL
        
        // Act & Assert
        RateLimitExceededException exception = assertThrows(
            RateLimitExceededException.class,
            () -> rateLimiterService.checkAndIncrement(testUserId, ChannelType.EMAIL)
        );
        
        assertEquals(ChannelType.EMAIL, exception.getChannel());
        assertEquals(10, exception.getLimit());
        
        // Verify increment was NOT called
        verify(valueOperations, never()).increment(anyString());
    }
    
    @Test
    @DisplayName("Should return remaining quota correctly")
    void getRemainingQuota_ReturnsCorrectCount() {
        // Arrange - user has sent 3 emails, limit is 10
        when(valueOperations.get(anyString())).thenReturn("3");
        
        // Act
        int remaining = rateLimiterService.getRemainingQuota(testUserId, ChannelType.EMAIL);
        
        // Assert
        assertEquals(7, remaining);
    }
    
    @Test
    @DisplayName("Should return full quota for new user")
    void getRemainingQuota_NewUser_ReturnsFullQuota() {
        // Arrange - user has no Redis entry (new user)
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // Act
        int remaining = rateLimiterService.getRemainingQuota(testUserId, ChannelType.EMAIL);
        
        // Assert
        assertEquals(10, remaining);
    }
    
    @Test
    @DisplayName("Should detect rate limited user")
    void isRateLimited_AtLimit_ReturnsTrue() {
        // Arrange - user at limit
        when(valueOperations.get(anyString())).thenReturn("10");
        
        // Act
        boolean isLimited = rateLimiterService.isRateLimited(testUserId, ChannelType.EMAIL);
        
        // Assert
        assertTrue(isLimited);
    }
    
    @Test
    @DisplayName("Should reset rate limit")
    void resetLimit_DeletesKey() {
        // Act
        rateLimiterService.resetLimit(testUserId, ChannelType.EMAIL);
        
        // Assert
        verify(redisTemplate).delete(anyString());
    }
}
