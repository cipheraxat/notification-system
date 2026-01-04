package com.notification.controller;

// =====================================================
// HealthController.java - Health Check Endpoints
// =====================================================
//
// Health endpoints are used by:
// - Kubernetes/Docker for container health
// - Load balancers to check if the service is alive
// - Monitoring systems to track service status
//
// Note: Spring Actuator provides similar endpoints at /actuator/health
// This is a custom one for additional application-specific checks.
//

import com.notification.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller for monitoring.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${spring.application.name:notification-system}")
    private String applicationName;
    
    public HealthController(
            DataSource dataSource,
            StringRedisTemplate redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Simple health check - just returns OK.
     * 
     * Use this for basic liveness probes.
     */
    @GetMapping
    @Operation(
        summary = "Basic health check",
        description = "Returns OK if the service is running"
    )
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Service is healthy", "OK"));
    }
    
    /**
     * Detailed health check with dependency status.
     * 
     * Checks:
     * - Database connection
     * - Redis connection
     * - Kafka connection
     */
    @GetMapping("/detailed")
    @Operation(
        summary = "Detailed health check",
        description = "Returns health status of all dependencies"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", applicationName);
        health.put("status", "UP");
        
        // Check each dependency
        Map<String, Object> dependencies = new HashMap<>();
        
        // Database check
        dependencies.put("database", checkDatabase());
        
        // Redis check
        dependencies.put("redis", checkRedis());
        
        // Kafka check (basic check only)
        dependencies.put("kafka", checkKafka());
        
        health.put("dependencies", dependencies);
        
        // Overall status is DOWN if any dependency is down
        boolean allHealthy = dependencies.values().stream()
            .allMatch(status -> "UP".equals(((Map<?, ?>) status).get("status")));
        
        if (!allHealthy) {
            health.put("status", "DEGRADED");
        }
        
        return ResponseEntity.ok(ApiResponse.success("Health check complete", health));
    }
    
    /**
     * Check database connectivity.
     */
    private Map<String, String> checkDatabase() {
        Map<String, String> status = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                status.put("status", "UP");
                status.put("message", "Database connection successful");
            } else {
                status.put("status", "DOWN");
                status.put("message", "Database connection invalid");
            }
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("message", e.getMessage());
        }
        return status;
    }
    
    /**
     * Check Redis connectivity.
     */
    private Map<String, String> checkRedis() {
        Map<String, String> status = new HashMap<>();
        try {
            String pong = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
            
            if ("PONG".equalsIgnoreCase(pong)) {
                status.put("status", "UP");
                status.put("message", "Redis connection successful");
            } else {
                status.put("status", "DOWN");
                status.put("message", "Unexpected Redis response");
            }
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("message", e.getMessage());
        }
        return status;
    }
    
    /**
     * Check Kafka connectivity.
     */
    private Map<String, String> checkKafka() {
        Map<String, String> status = new HashMap<>();
        try {
            // Check if Kafka producer is configured
            if (kafkaTemplate.getProducerFactory() != null) {
                status.put("status", "UP");
                status.put("message", "Kafka producer configured");
            } else {
                status.put("status", "DOWN");
                status.put("message", "Kafka producer not configured");
            }
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("message", e.getMessage());
        }
        return status;
    }
}
