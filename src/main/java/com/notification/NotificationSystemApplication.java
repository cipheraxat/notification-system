package com.notification;

// =====================================================
// NotificationSystemApplication.java - Main Entry Point
// =====================================================
//
// This is the starting point of our Spring Boot application.
// When you run this class, Spring Boot will:
// 1. Start an embedded Tomcat server
// 2. Scan for components (Controllers, Services, etc.)
// 3. Set up database connections
// 4. Connect to Kafka
// 5. Make our API available at http://localhost:8080
//

// Spring Boot imports
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Application Class
 * 
 * @SpringBootApplication is a convenience annotation that combines:
 * 
 * 1. @Configuration 
 *    - Marks this class as a source of bean definitions
 *    
 * 2. @EnableAutoConfiguration 
 *    - Tells Spring Boot to automatically configure beans based on 
 *      the dependencies we have (JPA, Kafka, Redis, etc.)
 *    
 * 3. @ComponentScan 
 *    - Tells Spring to scan for components (Controllers, Services, etc.)
 *      in this package and all sub-packages
 *
 * @EnableScheduling
 *    - Enables scheduled tasks (like retry processing)
 *    - We'll use @Scheduled annotation on methods to run them periodically
 */
@SpringBootApplication
@EnableScheduling
public class NotificationSystemApplication {

    /**
     * The main method - where everything starts!
     * 
     * SpringApplication.run() does the heavy lifting:
     * 1. Creates the Spring ApplicationContext
     * 2. Registers all Spring beans
     * 3. Starts the embedded web server
     * 
     * @param args Command-line arguments (if any)
     */
    public static void main(String[] args) {
        // Start the Spring Boot application
        // The first argument is the main class
        // The second argument is the command-line args
        SpringApplication.run(NotificationSystemApplication.class, args);
        
        // After this line runs, your API is live at:
        // - http://localhost:8080 (API endpoints)
        // - http://localhost:8080/swagger-ui.html (API documentation)
        // - http://localhost:8080/actuator/health (Health check)
    }
}
