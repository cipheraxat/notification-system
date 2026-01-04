package com.notification;

// =====================================================
// NotificationSystemApplicationTests.java - Main Test Class
// =====================================================
//
// This is the main test class that verifies the application
// can start up correctly.
//

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test to verify the application context loads.
 * 
 * @SpringBootTest starts the full Spring context.
 * @ActiveProfiles("test") uses application-test.yml configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationSystemApplicationTests {

    /**
     * This test verifies that the Spring context loads successfully.
     * 
     * If there are any configuration errors, missing beans, or
     * circular dependencies, this test will fail.
     */
    @Test
    void contextLoads() {
        // Just loading the context is the test
        // If it fails, we know there's a configuration problem
    }
}
