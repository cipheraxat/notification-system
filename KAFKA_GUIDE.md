# KAFKA_GUIDE.md - Understanding Data Flow in Notification System

## Overview

This guide explains how Apache Kafka enables asynchronous, scalable notification processing in our system. We use Kafka to decouple notification creation from delivery, allowing independent scaling of different notification channels.

## Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   REST API      │    │     Kafka       │    │   Consumers     │
│                 │    │                 │    │                 │
│ 1. Receive      │───▶│ 2. Queue        │───▶│ 3. Process      │
│    Request      │    │    Message      │    │    & Deliver    │
│                 │    │                 │    │                 │
│ 2. Save to DB   │    │ 4. Persist      │    │ 4. Update DB    │
│                 │    │    Message      │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Key Design Decisions

### Channel-Specific Topics (Alex Xu's Design Pattern)

Instead of one topic for all notifications, we use **separate topics per channel**:

| Channel | Topic | Partitions | Consumer Group | Purpose |
|---------|-------|------------|----------------|---------|
| Email | `notifications.email` | 3 | `notification-service-email` | High volume, batchable |
| SMS | `notifications.sms` | 2 | `notification-service-sms` | Rate-limited, expensive |
| Push | `notifications.push` | 4 | `notification-service-push` | Real-time, high priority |
| In-App | `notifications.in-app` | 3 | `notification-service-inapp` | Fast, local storage |

**Benefits:**
- **Independent Scaling**: Email might need 10 consumers, SMS only 2
- **Isolation**: Email failures don't affect push notifications
- **Priority Control**: Different processing priorities per channel
- **Monitoring**: Per-channel metrics and alerting

## Data Flow Step-by-Step

### 1. API Request Received

```bash
POST /api/v1/notifications
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "channel": "EMAIL",
  "content": "Welcome to our platform!"
}
```

### 2. Database Transaction

```java
// NotificationService.sendNotification()
@Transactional
public NotificationResponse sendNotification(SendNotificationRequest request) {
    // 1. Validate user exists
    User user = userRepository.findById(request.getUserId())
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    // 2. Check rate limits (Redis)
    boolean allowed = rateLimiter.checkAndIncrement(user.getId(), request.getChannel());

    // 3. Create notification entity
    Notification notification = Notification.builder()
        .user(user)
        .channel(request.getChannel())
        .content(request.getContent())
        .status(PENDING)
        .build();

    // 4. Save to database
    notification = notificationRepository.save(notification);

    // 5. Send to Kafka for async processing
    sendToKafka(notification);

    return mapToResponse(notification);
}
```

### 3. Kafka Message Production

```java
private void sendToKafka(Notification notification) {
    String key = notification.getId().toString();      // Partition key
    String value = notification.getId().toString();    // Message payload
    String topic = getTopicForChannel(notification.getChannel());

    kafkaTemplate.send(topic, key, value);
}
```

**Message Structure:**
- **Key**: Notification UUID (for partitioning)
- **Value**: Notification UUID (consumer fetches full details from DB)
- **Topic**: Channel-specific (e.g., `notifications.email`)

### 4. Kafka Message Storage

Kafka stores the message in the appropriate topic partition:

```
Topic: notifications.email
Partition: 1 (determined by key hash)
Offset: 42
Key: "35a3dbde-1ee1-4cc0-943a-d267ba1fbb44"
Value: "35a3dbde-1ee1-4cc0-943a-d267ba1fbb44"
```

### 5. Consumer Processing

Each channel has dedicated consumers:

```java
@KafkaListener(
    topics = "notifications.email",
    groupId = "notification-service-email",
    containerFactory = "kafkaListenerContainerFactory"
)
public void processEmailNotification(ConsumerRecord<String, String> record) {
    processNotification(record, acknowledgment, "EMAIL");
}
```

### 6. Message Processing Logic

```java
private void processNotification(ConsumerRecord<String, String> record, ...) {
    // 1. Extract notification ID from message
    String notificationIdStr = record.value();
    UUID notificationId = UUID.fromString(notificationIdStr);

    // 2. Fetch full notification from database
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow();

    // 3. Mark as processing
    notification.markAsProcessing();
    notificationRepository.save(notification);

    // 4. Dispatch to channel handler
    boolean success = channelDispatcher.dispatch(notification);

    // 5. Update final status
    if (success) {
        notification.markAsSent();
    } else {
        notification.markAsFailed();
    }
    notificationRepository.save(notification);

    // 6. Acknowledge message (remove from Kafka)
    acknowledgment.acknowledge();
}
```

## Channel-Specific Processing

### Email Channel
```java
// EmailChannelHandler.java
public boolean sendEmail(Notification notification) {
    // 1. Get user email from database
    String email = notification.getUser().getEmail();

    // 2. Render template (if needed)
    String content = templateService.render(notification);

    // 3. Send via SMTP/Email service
    return emailProvider.send(email, notification.getSubject(), content);
}
```

### SMS Channel
```java
// SmsChannelHandler.java
public boolean sendSms(Notification notification) {
    // 1. Get user phone from database
    String phone = notification.getUser().getPhone();

    // 2. Render template (if needed)
    String content = templateService.render(notification);

    // 3. Send via Twilio/Nexmo API
    return smsProvider.send(phone, content);
}
```

### Push Channel
```java
// PushChannelHandler.java
public boolean sendPush(Notification notification) {
    // 1. Get user device token from database
    String token = notification.getUser().getDeviceToken();

    // 2. Send via FCM/APNs
    return pushProvider.send(token, notification.getContent());
}
```

### In-App Channel
```java
// InAppChannelHandler.java
public boolean sendInApp(Notification notification) {
    // 1. Store in user's notification inbox
    // 2. Mark as delivered immediately
    return true; // Always succeeds
}
```

## Error Handling & Retry

### Consumer Error Handling

```java
try {
    // Process notification
    boolean success = channelDispatcher.dispatch(notification);

    if (success) {
        notification.markAsSent();
    } else {
        // Channel-specific retry logic
        handleRetry(notification);
    }
} catch (Exception e) {
    log.error("Failed to process notification {}", notificationId, e);

    // Mark as failed after max retries
    if (notification.getRetryCount() >= MAX_RETRIES) {
        notification.markAsFailed();
    } else {
        // Schedule retry
        notification.scheduleRetry();
    }
}
```

### Dead Letter Queue (DLQ)

Failed messages go to `notifications.dlq` topic for manual inspection:

```java
@KafkaListener(topics = "notifications.dlq")
public void processDeadLetter(ConsumerRecord<String, String> record) {
    log.error("Dead letter message: {}", record.value());
    // Manual intervention required
}
```

## Monitoring & Observability

### Kafka UI Dashboard
Access at: http://localhost:8090

**Monitor:**
- Message throughput per topic
- Consumer lag
- Partition distribution
- Error rates

### Key Metrics

```java
// Producer metrics
kafka_producer_record_send_total{topic="notifications.email"} 1250
kafka_producer_record_error_total{topic="notifications.email"} 5

// Consumer metrics
kafka_consumer_records_consumed_total{client_id="consumer-1"} 1200
kafka_consumer_records_lag{topic="notifications.email"} 45
```

## Configuration

### application.yml
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: notification-service
      auto-offset-reset: earliest
      enable-auto-commit: false

notification:
  kafka:
    topic:
      email: notifications.email
      sms: notifications.sms
      push: notifications.push
      in-app: notifications.in-app
      dlq: notifications.dlq
```

### Docker Setup
```yaml
# docker-compose.yml
services:
  kafka:
    image: confluentinc/cp-kafka:7.4.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,PLAINTEXT_INTERNAL://kafka:29092

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
```

## Scaling Considerations

### Horizontal Scaling
- **Add more consumer instances** for high-volume channels
- **Increase partitions** for better parallelism
- **Use consumer groups** for load balancing

### Vertical Scaling
- **Increase memory/CPU** for consumers during peak loads
- **Optimize database queries** to reduce processing time

### Production Considerations
- **Replication factor > 1** for fault tolerance
- **Monitoring and alerting** on consumer lag
- **Circuit breakers** for external service failures
- **Rate limiting** at producer level

## Troubleshooting

### Common Issues

1. **Consumer Lag**
   ```bash
   # Check consumer group status
   kafka-consumer-groups --bootstrap-server localhost:9092 --group notification-service-email --describe
   ```

2. **Message Loss**
   - Check producer acks configuration
   - Verify consumer acknowledgment

3. **Processing Errors**
   - Check application logs
   - Monitor DLQ topic

4. **Performance Issues**
   - Monitor partition distribution
   - Check consumer thread count
   - Optimize database queries

## Testing

### Unit Tests
```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"notifications.email"})
class NotificationServiceTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldSendNotificationToKafka() {
        // Given
        SendNotificationRequest request = createTestRequest();

        // When
        NotificationResponse response = notificationService.sendNotification(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(PENDING);

        // Verify message sent to Kafka
        await().atMost(5, SECONDS).until(() -> {
            ConsumerRecord<String, String> record = kafkaTestListener.getRecords().poll();
            return record != null && record.value().equals(response.getId());
        });
    }
}
```

This Kafka implementation provides a robust, scalable foundation for processing millions of notifications across multiple channels while maintaining reliability and observability.