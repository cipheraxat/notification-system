package com.notification.config;

// =====================================================
// KafkaConfig.java - Kafka Configuration
// =====================================================
//
// This configuration class sets up the Kafka consumers.
// 
// Architecture (Alex Xu's Design Pattern):
// - Channel-specific topics for independent scaling
// - Each channel can have different concurrency settings
// - Topics: notifications.email, notifications.sms, notifications.push, notifications.in-app
//
// Benefits of Channel-Specific Topics:
// 1. Independent Scaling: Email might need 10 consumers, SMS only 2
// 2. Isolation: Email delays don't affect push notifications
// 3. Priority: Can process push with higher priority than marketing emails
// 4. Monitoring: Easier to track metrics per channel
//

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for the notification consumers.
 * 
 * @Configuration marks this as a configuration class.
 * @EnableKafka enables detection of @KafkaListener annotations.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id:notification-service}")
    private String groupId;
    
    // Channel-specific topic names
    @Value("${notification.kafka.topic.email:notifications.email}")
    private String emailTopic;
    
    @Value("${notification.kafka.topic.sms:notifications.sms}")
    private String smsTopic;
    
    @Value("${notification.kafka.topic.push:notifications.push}")
    private String pushTopic;
    
    @Value("${notification.kafka.topic.in-app:notifications.in-app}")
    private String inAppTopic;
    
    @Value("${notification.kafka.topic.dlq:notifications.dlq}")
    private String dlqTopic;
    
    // ==================== Topic Creation ====================
    // 
    // Auto-create topics if they don't exist.
    // In production, topics should be pre-created with proper partitioning.
    //
    
    @Bean
    public NewTopic emailNotificationsTopic() {
        return TopicBuilder.name(emailTopic)
            .partitions(8)      // 8 partitions for high-volume email
            .replicas(3)        // 3 replicas for fault tolerance
            .build();
    }
    
    @Bean
    public NewTopic smsNotificationsTopic() {
        return TopicBuilder.name(smsTopic)
            .partitions(4)      // Fewer partitions (SMS is rate-limited by providers)
            .replicas(3)
            .build();
    }
    
    @Bean
    public NewTopic pushNotificationsTopic() {
        return TopicBuilder.name(pushTopic)
            .partitions(8)      // 8 partitions (push needs to be fast)
            .replicas(3)
            .build();
    }
    
    @Bean
    public NewTopic inAppNotificationsTopic() {
        return TopicBuilder.name(inAppTopic)
            .partitions(6)
            .replicas(3)
            .build();
    }
    
    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name(dlqTopic)
            .partitions(1)      // Single partition for DLQ
            .replicas(3)
            .build();
    }
    
    // ==================== Consumer Factory ====================
    
    /**
     * Create the consumer factory.
     * 
     * The consumer factory creates Kafka consumers with specific settings.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Kafka broker addresses
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Consumer group ID
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Deserializers for key and value
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // Don't auto-commit - we'll manually acknowledge after processing
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Start from earliest message if no offset exists
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Maximum records to fetch in one poll (higher = more throughput per poll cycle)
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        
        // Allow larger fetch sizes for throughput
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);   // Wait for 1KB before returning
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100);  // Or 100ms, whichever comes first
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * Create the listener container factory.
     * 
     * The container factory creates listener containers that manage
     * the lifecycle of Kafka consumers and message processing.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // Manual acknowledgment mode
        // We acknowledge after successful processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Number of concurrent consumers (threads)
        // Should match total partitions across topics for full parallelism
        factory.setConcurrency(10);
        
        return factory;
    }
}
