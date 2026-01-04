# 📋 Notification System - Interview Preparation Guide

> A comprehensive guide to explain your project and answer SDE-2 interview questions.

---

## Table of Contents

1. [60-Second Elevator Pitch](#60-second-elevator-pitch)
2. [Project Architecture Overview](#project-architecture-overview)
3. [Component Deep Dives](#component-deep-dives)
4. [Design Decisions & Trade-offs](#design-decisions--trade-offs)
5. [Interview Questions & Answers](#interview-questions--answers)
   - [System Design Questions](#system-design-questions)
   - [Architecture & Design Pattern Questions](#architecture--design-pattern-questions)
   - [Database & Data Modeling Questions](#database--data-modeling-questions)
   - [Kafka & Messaging Questions](#kafka--messaging-questions)
   - [Redis & Caching Questions](#redis--caching-questions)
   - [API Design Questions](#api-design-questions)
   - [Error Handling & Reliability Questions](#error-handling--reliability-questions)
   - [Scaling & Performance Questions](#scaling--performance-questions)
   - [Code Quality & Testing Questions](#code-quality--testing-questions)
   - [Behavioral/Situational Questions](#behaviorsituational-questions)
6. [How to Whiteboard This System](#how-to-whiteboard-this-system)
7. [Keywords to Use in Interviews](#keywords-to-use-in-interviews)

---

## 60-Second Elevator Pitch

> **Use this when asked: "Tell me about a project you've worked on"**

*"I built a multi-channel notification system following Alex Xu's system design principles. It sends notifications via Email, SMS, Push, and In-App channels.*

*The key technical highlights are:*
- *Asynchronous processing using Kafka for decoupling and reliability*
- *Rate limiting with Redis using the Token Bucket algorithm*
- *Template system for reusable message content*
- *Retry mechanism with exponential backoff for failed deliveries*
- *Clean layered architecture following SOLID principles*

*The system handles the full lifecycle: API receives request → validates → saves to PostgreSQL → publishes to Kafka → worker consumes and delivers via channel handlers → updates status with retry on failure."*

---

## Project Architecture Overview

### High-Level Flow

```
┌─────────────┐     ┌─────────────────────────────────────────────┐     ┌─────────────┐
│   Client    │────▶│         NOTIFICATION SERVICE                │────▶│  PostgreSQL │
│  (REST API) │     │                                             │     │  (Storage)  │
└─────────────┘     │  ┌───────────┐   ┌───────────┐   ┌───────┐ │     └─────────────┘
                    │  │Controller │──▶│  Service  │──▶│  Repo │ │
                    │  └───────────┘   └─────┬─────┘   └───────┘ │     ┌─────────────┐
                    │                        │                    │────▶│    Redis    │
                    │                        ▼                    │     │(Rate Limit) │
                    │                  ┌───────────┐              │     └─────────────┘
                    │                  │   Kafka   │              │
                    │                  │ (Publish) │              │     ┌─────────────┐
                    │                  └─────┬─────┘              │────▶│    Kafka    │
                    │                        │                    │     │   (Queue)   │
                    └────────────────────────┼────────────────────┘     └──────┬──────┘
                                             │                                 │
                    ┌────────────────────────┼─────────────────────────────────┘
                    │                        ▼
                    │  ┌─────────────────────────────────────────────────────┐
                    │  │              KAFKA CONSUMER (Worker)                 │
                    │  │                                                      │
                    │  │   ┌──────────────────┐    ┌───────────────────────┐ │
                    └──│──▶│ NotificationConsumer │──▶│  ChannelDispatcher  │ │
                       │   └──────────────────┘    └───────────┬───────────┘ │
                       │                                       │             │
                       │              ┌────────────────────────┼─────┐       │
                       │              ▼            ▼           ▼     ▼       │
                       │         ┌───────┐   ┌───────┐   ┌───────┐ ┌──────┐ │
                       │         │ Email │   │  SMS  │   │ Push  │ │In-App│ │
                       │         │Handler│   │Handler│   │Handler│ │Handler│
                       │         └───────┘   └───────┘   └───────┘ └──────┘ │
                       └─────────────────────────────────────────────────────┘
```

### Request Lifecycle (Step by Step)

| Step | Component | Action |
|------|-----------|--------|
| 1 | `NotificationController` | Receives POST request, validates input |
| 2 | `NotificationService` | Checks rate limit via Redis |
| 3 | `TemplateService` | Processes template (if using one) |
| 4 | `NotificationRepository` | Saves notification with PENDING status |
| 5 | `KafkaTemplate` | Publishes notification ID to Kafka topic |
| 6 | **API Response** | Returns 202 Accepted with notification ID |
| 7 | `NotificationConsumer` | Picks up message from Kafka |
| 8 | `ChannelDispatcher` | Routes to correct handler (Email/SMS/Push/In-App) |
| 9 | `EmailChannelHandler` (etc.) | Sends via external provider (SendGrid/Twilio) |
| 10 | `NotificationRepository` | Updates status to SENT or schedules retry |

---

## Component Deep Dives

### 1. Rate Limiter (Token Bucket Algorithm)

**Location:** `RateLimiterService.java`

```
How it works:
┌────────────────────────────────────────────┐
│  User requests to send EMAIL notification  │
└─────────────────┬──────────────────────────┘
                  ▼
┌────────────────────────────────────────────┐
│  Build Redis key: "ratelimit:user123:EMAIL" │
└─────────────────┬──────────────────────────┘
                  ▼
┌────────────────────────────────────────────┐
│  Get current count from Redis              │
│  Example: 7 (user sent 7 emails this hour) │
└─────────────────┬──────────────────────────┘
                  ▼
┌────────────────────────────────────────────┐
│  Compare: 7 < 10 (limit)?                  │
│  YES → Increment counter, allow request    │
│  NO  → Throw RateLimitExceededException    │
└────────────────────────────────────────────┘
```

**Key Code:**
```java
// Redis key format: "ratelimit:{userId}:{channel}"
String key = String.format("ratelimit:%s:%s", userId, channel);

// Atomic increment with TTL
Long newCount = redisTemplate.opsForValue().increment(key);
if (newCount == 1) {
    redisTemplate.expire(key, Duration.ofSeconds(3600)); // 1 hour window
}
```

**Why Token Bucket?**
- Simple to implement and explain
- Allows bursts (up to bucket capacity)
- Self-resets via Redis TTL (no cleanup job needed)

---

### 2. Message Queue (Kafka)

**Why Kafka?**
| Without Kafka | With Kafka |
|---------------|------------|
| API waits for email to send (slow) | API returns immediately (fast) |
| If email fails, request fails | Worker retries independently |
| Can't handle traffic spikes | Queue absorbs spikes |
| Single point of failure | Decoupled, fault-tolerant |

**Key Design Decisions:**
1. **Single Topic:** `notifications` - Simple, no complex routing
2. **Key = Notification ID:** Ensures ordering per notification
3. **Manual Acknowledgment:** Only commit offset after successful processing
4. **Concurrency = 3:** Multiple consumer threads for parallelism

**Configuration (KafkaConfig.java):**
```java
// Don't auto-commit - we'll manually acknowledge
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

// Manual acknowledgment mode
factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
```

---

### 3. Retry Mechanism

**Retry Flow:**
```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   PENDING   │────▶│  PROCESSING  │────▶│    SENT     │ (Success!)
└─────────────┘     └──────┬───────┘     └─────────────┘
                           │
                           │ (Failure)
                           ▼
                    ┌──────────────┐
                    │ Schedule     │
                    │ Retry with   │
                    │ Backoff      │
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
        retry_count < max?        retry_count >= max?
              │                         │
              ▼                         ▼
        Back to PENDING           Status = FAILED
        (with next_retry_at)
```

**Exponential Backoff:**
```java
// Calculate next retry time with exponential backoff
// Retry 1: 1 min, Retry 2: 2 min, Retry 3: 4 min...
long delayMinutes = (long) Math.pow(2, retryCount - 1);
this.nextRetryAt = OffsetDateTime.now().plusMinutes(delayMinutes);
```

**Two Retry Mechanisms:**
1. **Kafka Consumer:** Immediate retry on failure
2. **RetryScheduler:** Picks up PENDING notifications due for retry (runs every 60s)

---

### 4. Channel Handler Pattern (Strategy Pattern)

**Interface:**
```java
public interface ChannelHandler {
    ChannelType getChannelType();
    boolean send(Notification notification);
    default boolean canHandle(Notification notification) { ... }
}
```

**Implementations:**
- `EmailChannelHandler` → SendGrid/SES
- `SmsChannelHandler` → Twilio
- `PushChannelHandler` → Firebase FCM
- `InAppChannelHandler` → Database storage

**Dispatcher (O(1) Routing):**
```java
// Build handler map at startup
this.handlers = handlerList.stream()
    .collect(Collectors.toMap(
        ChannelHandler::getChannelType,
        Function.identity()
    ));

// Dispatch: O(1) lookup
ChannelHandler handler = handlers.get(notification.getChannel());
return handler.send(notification);
```

**Why Strategy Pattern?**
- Open/Closed Principle: Add new channels without modifying existing code
- Single Responsibility: Each handler knows only its channel
- Easy to test: Mock individual handlers

---

## Design Decisions & Trade-offs

| Decision | Why | Trade-off |
|----------|-----|-----------|
| **Kafka over RabbitMQ** | Better durability, replay capability, high throughput | More complex to operate |
| **Redis for rate limiting** | Fast (in-memory), TTL support, atomic operations | Additional infrastructure |
| **PostgreSQL** | ACID compliance, JSON support, reliability | Harder to scale horizontally |
| **UUID primary keys** | Globally unique, can generate client-side | Larger than integers, slower indexes |
| **Save-then-publish** | Notification survives Kafka failure | Possible duplicate sends |
| **Single Kafka topic** | Simple, easy to manage | Less flexibility for priority queues |
| **Polling retry scheduler** | Simple, reliable | Less immediate than push-based |

---

## Interview Questions & Answers

### System Design Questions

---

**Q1: Walk me through how a notification is sent in your system.**

**Answer:**
"When a client sends a POST request to `/api/v1/notifications`:

1. The **Controller** validates the request body using Bean Validation
2. The **Service** checks rate limits via Redis - if exceeded, returns 429
3. If using a template, the **TemplateService** processes variable substitution
4. The notification is saved to **PostgreSQL** with PENDING status
5. The notification ID is published to **Kafka**
6. We return **202 Accepted** immediately (async processing)
7. A **Kafka Consumer** picks up the message
8. The **ChannelDispatcher** routes to the right handler (Email/SMS/Push/In-App)
9. The handler sends via the external provider
10. Status is updated to SENT, or retry is scheduled if failed"

---

**Q2: Why did you use a message queue? Why Kafka specifically?**

**Answer:**
"I used a message queue for three main reasons:

1. **Decoupling:** The API returns immediately instead of waiting for email to send. Users get a fast response.

2. **Reliability:** If SendGrid is down, the notification is safe in Kafka. The worker will retry later.

3. **Handling spikes:** If we get 10,000 notifications at once, Kafka absorbs the load. Workers process at their own pace.

I chose Kafka over RabbitMQ because:
- **Durability:** Messages are persisted to disk
- **Replay:** If something goes wrong, we can replay messages
- **High throughput:** Kafka handles millions of messages per second
- **Consumer groups:** Easy horizontal scaling of workers"

---

**Q3: How does your rate limiting work?**

**Answer:**
"I implemented the **Token Bucket algorithm** using Redis:

1. Each user+channel combination has a Redis key like `ratelimit:user123:EMAIL`
2. The value is a counter of notifications sent in the current hour
3. When a request comes in, I check: is counter < limit?
4. If yes: increment counter and proceed
5. If no: throw `RateLimitExceededException` (HTTP 429)

The counter auto-expires after 1 hour via Redis TTL, so it self-resets.

Why Redis?
- **Fast:** In-memory, sub-millisecond lookups
- **Atomic:** `INCR` operation is thread-safe
- **TTL:** Built-in expiration, no cleanup job needed
- **Shared:** Multiple app instances see the same counters"

---

**Q4: How do you handle failures? What if an email fails to send?**

**Answer:**
"I have a two-layer retry mechanism:

**Layer 1 - Immediate (Kafka Consumer):**
When delivery fails, I update the notification with:
- `retry_count` incremented
- `next_retry_at` set with exponential backoff (1min, 2min, 4min...)
- `status` back to PENDING
- `last_error` with the failure reason

**Layer 2 - Scheduled (RetryScheduler):**
A cron job runs every 60 seconds, finds PENDING notifications where `next_retry_at < now`, and reprocesses them.

**Safeguards:**
- Max 5 retries, then status = FAILED
- Stuck notification reset (if PROCESSING > 10 minutes, reset to PENDING)
- All failures are logged for monitoring"

---

**Q5: What happens if Kafka is down when you try to publish?**

**Answer:**
"The notification is safe because of my **save-then-publish** pattern:

1. I save to PostgreSQL first (transactional, durable)
2. Then publish to Kafka

If Kafka publish fails:
- I catch the exception and log it
- The transaction still commits (notification is in DB)
- The RetryScheduler will find PENDING notifications and reprocess them

This ensures **at-least-once delivery** - we might send duplicates, but we'll never lose a notification."

---

### Architecture & Design Pattern Questions

---

**Q6: What design patterns did you use?**

**Answer:**
"Several patterns:

1. **Strategy Pattern (Channel Handlers):**
   - `ChannelHandler` interface with `send()` method
   - Implementations: `EmailChannelHandler`, `SmsChannelHandler`, etc.
   - `ChannelDispatcher` routes to the right handler
   - Benefit: Add new channels without changing existing code (Open/Closed Principle)

2. **Repository Pattern:**
   - Spring Data JPA repositories abstract database access
   - Service layer doesn't know SQL or JPA details
   - Easy to switch databases or add caching

3. **Builder Pattern:**
   - Used for constructing `Notification` and `ApiResponse` objects
   - Makes object creation readable: `Notification.builder().user(user).channel(EMAIL).build()`

4. **Template Method Pattern (implicit):**
   - `ChannelHandler.canHandle()` has a default implementation
   - Subclasses can override for custom validation"

---

**Q7: How did you ensure loose coupling between components?**

**Answer:**
"Three main techniques:

1. **Dependency Injection:**
   Constructor injection everywhere. Components receive their dependencies, they don't create them. Makes testing easy - just pass mocks.

2. **Interface-based design:**
   `ChannelHandler` interface means the dispatcher doesn't know about specific handlers. I can add `WhatsAppChannelHandler` without touching dispatcher code.

3. **Event-driven architecture:**
   The API and worker are decoupled via Kafka. They don't call each other directly. The API publishes events, workers consume them."

---

**Q8: How would you add a new notification channel (e.g., WhatsApp)?**

**Answer:**
"It's a 2-step process thanks to the Strategy Pattern:

1. **Create the handler:**
```java
@Component
public class WhatsAppChannelHandler implements ChannelHandler {
    @Override
    public ChannelType getChannelType() {
        return ChannelType.WHATSAPP;
    }
    
    @Override
    public boolean send(Notification notification) {
        // Call WhatsApp Business API
    }
}
```

2. **Add the enum value:**
```java
public enum ChannelType {
    EMAIL, SMS, PUSH, IN_APP, WHATSAPP
}
```

That's it. Spring auto-discovers the new handler, the dispatcher registers it automatically."

---

### Database & Data Modeling Questions

---

**Q9: Explain your database schema design.**

**Answer:**
"I have 4 main tables:

1. **users:** Basic user info (email, phone, device_token)
2. **user_preferences:** Channel preferences per user (enabled/disabled, quiet hours)
3. **notification_templates:** Reusable message templates with variable placeholders
4. **notifications:** The core table - one row per notification sent

Key design decisions:
- **UUIDs as primary keys:** Globally unique, can generate client-side
- **Timestamps with timezone:** All times stored in UTC
- **Indexes on common queries:** user_id, status, created_at
- **Composite index:** (user_id, created_at DESC) for inbox pagination

The notifications table is the busiest, so I indexed heavily:
```sql
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
```"

---

**Q10: How do you handle the notifications table growing very large?**

**Answer:**
"For a production system at scale, I would consider:

1. **Time-based partitioning:**
   Partition by month. Old partitions can be archived or dropped.
   ```sql
   CREATE TABLE notifications (...) PARTITION BY RANGE (created_at);
   ```

2. **Archival strategy:**
   Move notifications older than 90 days to an archive table or cold storage (S3).

3. **Read replicas:**
   Route inbox queries to read replicas to reduce load on primary.

4. **Caching hot data:**
   Cache recent notifications in Redis for fast inbox loading.

However, for the current scope, the indexes I have are sufficient for millions of records."

---

**Q11: Why UUIDs instead of auto-increment IDs?**

**Answer:**
"Three reasons:

1. **Globally unique:** No collisions across distributed systems or merging databases
2. **Generate client-side:** Don't need a database round-trip to get an ID
3. **Security:** Doesn't reveal how many records exist (ID enumeration attack prevention)

Trade-offs I accept:
- Larger (16 bytes vs 4 bytes for int)
- Slightly slower index performance
- Not human-readable

For this use case, the benefits outweigh the costs."

---

### Kafka & Messaging Questions

---

**Q12: How do you ensure messages aren't lost in Kafka?**

**Answer:**
"Multiple safeguards:

1. **Producer side:**
   - Default acks=all (wait for all replicas to acknowledge)
   - Kafka persists to disk before acknowledging

2. **Consumer side:**
   - Manual acknowledgment (`AckMode.MANUAL`)
   - Only commit offset after successful processing
   - If consumer crashes, message will be redelivered

3. **Application level:**
   - Save to PostgreSQL before publishing to Kafka
   - If Kafka fails, RetryScheduler picks up PENDING notifications

This gives **at-least-once delivery**. We might process duplicates, but never lose messages."

---

**Q13: How do you handle duplicate messages?**

**Answer:**
"Kafka provides at-least-once delivery, so duplicates can happen. I handle this with:

1. **Idempotent processing:**
   Before processing, I check: `if (notification.getStatus() != PENDING) { skip; }`
   If already SENT, I skip it.

2. **Database as source of truth:**
   The Kafka message only contains the notification ID. I always fetch current state from PostgreSQL before processing.

3. **Unique constraints:**
   If I tried to insert a duplicate, the database would reject it.

This makes my consumer idempotent - processing the same message twice has no additional effect."

---

**Q14: Why single topic instead of topic-per-channel?**

**Answer:**
"I chose simplicity. A single `notifications` topic means:

- Fewer topics to manage
- Easier monitoring
- Single consumer configuration

**When would I use multiple topics?**
If I needed:
- Different retention per channel (SMS logs shorter than email)
- Different processing rates (priority queue - HIGH topic processed first)
- Independent scaling (more SMS workers, fewer email workers)

For this project's scope, single topic is sufficient. It's a phase-2 optimization if needed."

---

### Redis & Caching Questions

---

**Q15: Why Redis for rate limiting instead of in-memory?**

**Answer:**
"In a distributed system with multiple application instances:

**In-memory (HashMap):**
- Instance A counts: 5 requests
- Instance B counts: 5 requests
- User actually sent 10, but each instance thinks only 5

**Redis (shared):**
- Both instances read/write to same counter
- Accurate count across all instances

Also, Redis gives me:
- **Atomic operations:** INCR is thread-safe
- **TTL:** Counter auto-expires, no cleanup needed
- **Persistence:** Survives app restart"

---

**Q16: What if Redis goes down?**

**Answer:**
"I have a few options:

1. **Fail open (current):** If Redis is unavailable, allow the request. Better user experience, but rate limiting is bypassed.

2. **Fail closed:** Reject all requests if Redis is down. Safer, but poor UX.

3. **Local fallback:** Maintain an in-memory rate limiter as backup. Less accurate but functional.

For production, I'd recommend:
- Redis cluster/sentinel for high availability
- Health check endpoint to monitor Redis
- Alerting when Redis becomes unavailable"

---

### API Design Questions

---

**Q17: Why return 202 Accepted instead of 200 OK?**

**Answer:**
"HTTP 202 means 'request accepted for processing, but not completed yet.'

This is semantically correct because:
- The notification is queued, not sent
- Actual delivery happens asynchronously
- Client should poll or wait for webhook if they need confirmation

200 OK would imply the action is complete, which is misleading.

I also return the notification ID so clients can check status later:
```json
{
  "success": true,
  "data": {
    "id": "abc-123",
    "status": "PENDING"
  }
}
```"

---

**Q18: How do you handle validation errors?**

**Answer:**
"I use Bean Validation annotations and a global exception handler:

**Request DTO:**
```java
@NotNull(message = "User ID is required")
private UUID userId;

@NotNull(message = "Channel is required")
private ChannelType channel;
```

**Global Exception Handler:**
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(...) {
    // Extract field errors
    Map<String, String> errors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .collect(toMap(FieldError::getField, FieldError::getDefaultMessage));
    
    return ResponseEntity.badRequest().body(ApiResponse.error(errors));
}
```

**Response:**
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "userId": "User ID is required",
    "channel": "Channel is required"
  }
}
```"

---

### Error Handling & Reliability Questions

---

**Q19: How do you handle partial failures in bulk notifications?**

**Answer:**
"For bulk notifications, I use a **best-effort** approach:

1. Process each user independently in a loop
2. Catch exceptions per-user, don't fail the whole batch
3. Track successes and failures separately
4. Return a detailed report

```java
for (UUID userId : request.getUserIds()) {
    try {
        // Process notification
        response.addSuccess(notificationId);
    } catch (Exception e) {
        response.addFailure(userId, e.getMessage());
    }
}
```

**Response:**
```json
{
  "totalRequested": 100,
  "successCount": 95,
  "failedCount": 5,
  "successIds": ["abc", "def", ...],
  "failures": [
    {"userId": "xyz", "reason": "Rate limit exceeded"}
  ]
}
```"

---

**Q20: What monitoring would you add for production?**

**Answer:**
"I would add:

1. **Metrics (Prometheus/Micrometer):**
   - Notifications sent per channel (counter)
   - Notification latency (histogram)
   - Kafka consumer lag
   - Rate limit rejections
   - Retry counts

2. **Logging:**
   - Structured JSON logs
   - Correlation IDs for tracing
   - Log aggregation (ELK stack)

3. **Alerting:**
   - Failed notification rate > 5%
   - Kafka consumer lag > 1000
   - Redis unavailable
   - Retry queue growing

4. **Health endpoints:**
   - `/health` - Basic health
   - `/health/ready` - Dependencies ready
   - `/health/live` - App alive"

---

### Scaling & Performance Questions

---

**Q21: How would you scale this system to handle 10x traffic?**

**Answer:**
"I would scale horizontally at multiple layers:

1. **API Layer:**
   - Run multiple instances behind a load balancer
   - They're stateless, so easy to scale

2. **Kafka:**
   - Increase topic partitions
   - More partitions = more parallel consumers

3. **Workers:**
   - Add more consumer instances (same group ID)
   - Kafka distributes partitions among them

4. **Database:**
   - Read replicas for inbox queries
   - Connection pooling (HikariCP)
   - Consider sharding by user_id for extreme scale

5. **Redis:**
   - Redis Cluster for distributed rate limiting
   - Consistent hashing for key distribution"

---

**Q22: What's the bottleneck in your current design?**

**Answer:**
"The most likely bottlenecks:

1. **Database writes:** Every notification = one INSERT. At high volume, this could slow down.
   **Solution:** Batch inserts, or use a write-optimized DB like Cassandra.

2. **External providers:** SendGrid/Twilio have their own rate limits.
   **Solution:** Respect their limits, queue excess, retry with backoff.

3. **Single Kafka partition:** If all messages go to one partition, one consumer handles everything.
   **Solution:** Use user_id as partition key to distribute load.

For the current scale, PostgreSQL with proper indexes handles millions of notifications fine."

---

### Code Quality & Testing Questions

---

**Q23: How do you test this system?**

**Answer:**
"Multiple test levels:

1. **Unit Tests:**
   - Test services with mocked dependencies
   - `RateLimiterServiceTest` - mock Redis
   - `NotificationServiceTest` - mock repos, Kafka

2. **Integration Tests:**
   - Use real PostgreSQL (via Docker)
   - Test full request flow
   - Verify database state

3. **Manual/E2E Testing:**
   - Swagger UI for API testing
   - Postman collection included
   - Docker Compose for local environment

**Example unit test:**
```java
@Test
void shouldRejectWhenRateLimitExceeded() {
    when(redisTemplate.opsForValue().get(anyString())).thenReturn("10");
    
    assertThrows(RateLimitExceededException.class, () -> 
        rateLimiterService.checkAndIncrement(userId, EMAIL)
    );
}
```"

---

**Q24: How do you handle configuration across environments?**

**Answer:**
"Spring profiles and externalized configuration:

1. **application.yml:** Default configuration
2. **application-test.yml:** Test overrides (different DB, disabled schedulers)
3. **Environment variables:** Override for production (DB_URL, KAFKA_SERVERS)

**Example:**
```yaml
# application.yml
notification:
  rate-limit:
    email: ${RATE_LIMIT_EMAIL:10}  # Default 10, override via env var
```

This follows 12-factor app principles - config in environment, not code."

---

### Behavioral/Situational Questions

---

**Q25: What was the most challenging part of building this?**

**Answer:**
"The retry mechanism with exactly-once semantics was tricky.

**Challenge:** Ensuring a failed notification gets retried without:
- Losing it (if worker crashes)
- Sending duplicates (if processed twice)

**Solution:**
1. Save to DB before publishing to Kafka (notification survives Kafka failure)
2. Check status before processing (skip if already SENT)
3. Manual Kafka acknowledgment (only commit after success)
4. Scheduled job as backup (catches anything missed)

This gives at-least-once delivery with idempotent processing."

---

**Q26: What would you do differently if you rebuilt this?**

**Answer:**
"A few things:

1. **Event Sourcing:** Store notification events instead of just current state. Enables audit trails and replay.

2. **Dead Letter Queue:** Instead of max retries → FAILED, move to a DLQ for manual investigation.

3. **Circuit Breaker:** If SendGrid is down, stop trying and fail fast. Prevent cascade failures.

4. **Outbox Pattern:** Instead of publish after save, use a transactional outbox for guaranteed delivery.

These are production-grade improvements I'd add in a real system."

---

**Q27: How did you decide what to include vs. exclude?**

**Answer:**
"I followed Alex Xu's approach: solve the core problem well, explicitly scope out complexity.

**Included (core features):**
- Multi-channel delivery ✓
- Rate limiting ✓
- Async processing ✓
- Retry mechanism ✓
- Template system ✓

**Excluded (complexity traps):**
- Analytics dashboard ✗
- A/B testing ✗
- Complex scheduling (cron) ✗
- Multi-tenancy ✗

This keeps the project explainable in an interview without inviting questions I can't answer well."

---

## How to Whiteboard This System

When asked to design on a whiteboard:

### Step 1: Clarify Requirements (2 min)
- "What channels? Email, SMS, Push, In-App?"
- "Expected volume? 1000/sec?"
- "Delivery guarantee needed? At-least-once?"

### Step 2: High-Level Design (5 min)
```
Client → API → DB → Queue → Worker → Provider
                ↓
              Redis (rate limit)
```

### Step 3: Deep Dive (8 min)
Pick ONE area based on interviewer interest:
- Rate limiting algorithm
- Retry mechanism
- Database schema
- Kafka configuration

### Step 4: Trade-offs (2 min)
- "I chose X over Y because..."
- "If we needed Z, I would add..."

---

## Keywords to Use in Interviews

**Architecture:**
- Microservices, Layered Architecture, Clean Architecture
- Dependency Injection, Inversion of Control
- Event-Driven, Async Processing

**Patterns:**
- Strategy Pattern, Repository Pattern, Builder Pattern
- Token Bucket Algorithm
- Exponential Backoff

**Reliability:**
- At-least-once delivery, Idempotency
- Circuit Breaker, Retry with Backoff
- Dead Letter Queue

**Scalability:**
- Horizontal Scaling, Stateless Services
- Partitioning, Sharding
- Consumer Groups

**Data:**
- ACID, Eventually Consistent
- Indexing, Composite Index
- Connection Pooling

---

## Quick Reference Card

| Topic | Your Answer |
|-------|-------------|
| **Why Kafka?** | Decoupling, reliability, handles spikes |
| **Why Redis?** | Fast, atomic, TTL for rate limit |
| **Why PostgreSQL?** | ACID, reliable, good enough for scale |
| **Rate limiting algo?** | Token Bucket |
| **Retry strategy?** | Exponential backoff, max 5 retries |
| **Design pattern?** | Strategy (handlers), Repository (data) |
| **Delivery guarantee?** | At-least-once |
| **Handle duplicates?** | Idempotent processing (check status) |

---

*Good luck with your interviews! 🚀*
