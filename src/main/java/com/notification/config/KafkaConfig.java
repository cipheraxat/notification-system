package com.notification.config;

// =====================================================
// KafkaConfig.java - Kafka Configuration
// =====================================================
//
// This configuration class sets up the Kafka consumer.
// Most settings come from application.yml, but we need
// to configure the listener container factory here.
//

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for the notification consumer.
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
        
        // Maximum records to fetch in one poll
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        
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
        // Each thread processes messages independently
        factory.setConcurrency(3);
        
        return factory;
    }
}
