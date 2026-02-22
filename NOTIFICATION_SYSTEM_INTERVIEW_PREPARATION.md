# Notification System — Interview Preparation Guide

> Master your notification system project for SDE-2 interviews.
> This guide covers architecture, design decisions, and 31 practice Q&A.

---

## How to Use This Guide

| Time Available | What to Read | Sections |
|---------------|-------------|----------|
| **10 minutes** | Quick scan before walking in | Part 1: Quick Reference Card + Elevator Pitch |
| **1 hour** | Core understanding | Part 1 + Part 2 (Architecture & Components) |
| **3 hours** | Full preparation | Parts 1-4 (everything except AWS) |
| **Full day** | Complete mastery | All 5 parts including AWS deployment |

---

## Table of Contents

**Part 1 — Quick Review** *(read 10 min before your interview)*
- [Quick Reference Card](#quick-reference-card)
- [60-Second Elevator Pitch](#60-second-elevator-pitch)
- [How to Whiteboard This System](#how-to-whiteboard-this-system)
- [Keywords to Use in Interviews](#keywords-to-use-in-interviews)

**Part 2 — Understand the System** *(build your mental model)*
- [Architecture & Request Flow](#project-architecture-overview)
- [Core Components](#component-deep-dives) — Rate Limiter, Caching, Kafka, Retry, Channel Handlers
- [Database Design](#database-schema-design) — Schema, Indexes, Design Rationale
- [Design Decisions & Trade-offs](#design-decisions--trade-offs)

**Part 3 — Performance & Operations** *(show you think about production)*
- [Performance Tuning (10k req/sec)](#performance-tuning--throughput-10k-reqsec)
- [Monitoring & Stress Testing](#monitoring--stress-testing)
- [End-to-End Data Lifecycle Reference](#end-to-end-data-lifecycle-deep-dive)

**Part 4 — Interview Q&A** *(31 questions with model answers)*
- [System Design (Q1-Q5)](#system-design-questions)
- [Architecture & Patterns (Q6-Q8)](#architecture--design-pattern-questions)
- [Database (Q9-Q11)](#database--data-modeling-questions)
- [Kafka & Messaging (Q12-Q14)](#kafka--messaging-questions)
- [Redis & Caching (Q15-Q20)](#redis--caching-questions)
- [API Design (Q21-Q22)](#api-design-questions)
- [Error Handling (Q23-Q24)](#error-handling--reliability-questions)
- [Scaling & Performance (Q25-Q26)](#scaling--performance-questions)
- [Code Quality & Testing (Q27-Q28)](#code-quality--testing-questions)
- [Behavioral (Q29-Q31)](#behaviorsituational-questions)

**Part 5 — AWS Production Deployment** *(advanced — for principal/staff-level depth)*
- [Global Architecture & Service Mapping](#aws-production-deployment-multi-country-scenario)
- [Multi-Region, Compliance, Cost, DR](#multi-region-deployment-strategy)

---

# Part 1 — Quick Review

> *Read this section 10 minutes before your interview. It gives you the cheat sheet, your opening pitch, and a whiteboard plan.*

---

## Quick Reference Card


| Topic | Your Answer |
|-------|-------------|
| **Why Kafka?** | Decoupling, reliability, handles spikes |
| **Why Redis?** | Rate limiting + caching, fast, atomic, TTL |
| **What do you cache?** | User lookups (by ID, email, phone), device tokens, templates |
| **Cache strategy?** | TTL 1hr, eviction on changes, Jackson serialization |
| **Why PostgreSQL?** | ACID, reliable, good enough for scale |
| **Rate limiting algo?** | Token Bucket |
| **Retry strategy?** | Exponential backoff (5^n), max 3 retries |
| **Design pattern?** | Strategy (handlers), Repository (data) |
| **Delivery guarantee?** | At-least-once |
| **Handle duplicates?** | Idempotent processing (check status) |


---

## 60-Second Elevator Pitch


> **Use this when asked: "Tell me about a project you've worked on"**

*I built a multi-channel notification system following system design principles. It sends notifications via Email, SMS, Push, and In-App channels.*

*The key technical highlights are:*
- *Asynchronous processing using Kafka for decoupling and reliability*
- *Rate limiting with Redis using the Token Bucket algorithm*
- *Redis caching for user lookups and notification templates to reduce database load*
- *Template system for reusable message content*
- *Retry mechanism with exponential backoff for failed deliveries*
- *Clean layered architecture following SOLID principles*

*The system handles the full lifecycle: API receives request → validates → saves to PostgreSQL → publishes to Kafka → worker consumes and delivers via channel handlers → updates status with retry on failure."*


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
- Token Bucket Algorithm, Cache-Aside Pattern
- Exponential Backoff

**Caching:**
- Spring Cache Abstraction, @Cacheable, @CacheEvict
- TTL (Time To Live), Cache Invalidation
- Serialization, Jackson ObjectMapper

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

# Part 2 — Understand the System

> *Build your mental model of how the system works. Read this section when you have 1+ hours to prepare.*

---

## Project Architecture Overview


### High-Level Flow

```
┌─────────────┐     ┌─────────────────────────────────────────────┐     ┌─────────────┐
│   Client    │────▶│         NOTIFICATION SERVICE                │────▶│  PostgreSQL │
│  (REST API) │     │                                             │     │  (Storage)  │
└─────────────┘     │  ┌───────────┐   ┌───────────┐   ┌───────┐  │     └─────────────┘
                    │  │Controller │──▶│  Service  │──▶│  Repo │  │
                    │  └───────────┘   └─────┬─────┘   └───────┘  │     ┌─────────────┐
                    │                        │                    │────▶│    Redis    │
                    │                        ▼                    │     │(Cache + Rate│
                    │                  ┌───────────┐              │     │   Limit)    │
                    │                  │   Kafka   │              │     └─────────────┘
                    │                  │   Kafka   │              │
                    │                  │ (Publish) │              │     ┌─────────────┐
                    │                  └─────┬─────┘              │────▶│    Kafka    │
                    │                        │                    │     │   (Queue)   │
                    └────────────────────────┼────────────────────┘     └──────┬──────┘
                                             │                                 │
                    ┌────────────────────────┼─────────────────────────────────┘
                    │                        ▼
                    │  ┌─────────────────────────────────────────────────────┐
                    │  │              KAFKA CONSUMER (Worker)                │
                    │  │                                                     │
                    │  │   ┌──────────────────┐    ┌───────────────────────┐ │
                    └──│──▶│ NotificationConsumer │──▶│  ChannelDispatcher │ │
                       │   └──────────────────┘    └───────────┬───────────┘ │
                       │                                       │             │
                       │              ┌────────────────────────┼─────┐       │
                       │              ▼            ▼           ▼     ▼       │
                       │         ┌───────┐   ┌───────┐   ┌───────┐ ┌──────┐  │
                       │         │ Email │   │  SMS  │   │ Push  │ │In-App│  │
                       │         │Handler│   │Handler│   │Handler│ │Handler│ |
                       │         └───────┘   └───────┘   └───────┘ └──────┘  │
                       └─────────────────────────────────────────────────────┘
```

### Request Lifecycle (Step by Step)

| Step | Component | Action | Data Flow Details |
|------|-----------|--------|-------------------|
| 1 | `NotificationController` | Receives POST request, validates input | **Input:** JSON request body<br>```json<br>{<br>  "userId": "550e8400-e29b-41d4-a716-446655440001",<br>  "channel": "EMAIL",<br>  "templateName": "welcome-email",<br>  "templateVariables": {"userName": "John"}<br>}<br>```<br>**Validation:** Bean Validation on `SendNotificationRequest` DTO |
| 2 | `NotificationService` | Validates user (cached), checks dedup + rate limit via Redis | **User Lookup:** `userService.findById(userId)` (cached in Redis with key `"users::id:{id}"`)<br>**Dedup Check:** `deduplicationService.isDuplicate(eventId)` (Redis key `"event:{eventId}"`)<br>**Rate Limit Check:** `rateLimiterService.checkAndIncrement(userId, channel)`<br>**Redis Key:** `"ratelimit:{userId}:{channel}"`<br>**Throws:** `RateLimitExceededException` if limit exceeded |
| 3 | `TemplateService` | Processes template (cached lookup) | **Input:** `templateName`, `templateVariables`<br>**Template Lookup:** `templateService.getTemplateByName(name)` (cached with key `"templates::name:{name}"`)<br>**Processing:** Variable substitution in template content<br>**Output:** `subject`, `content` strings<br>**Example:** Template `"Welcome {{userName}}!"` → `"Welcome John!"` |
| 4 | `NotificationRepository` | Saves notification with PENDING status | **Entity Creation:**<br>```java<br>Notification notification = Notification.builder()<br>    .user(user)<br>    .channel(request.getChannel())<br>    .priority(request.getPriority())<br>    .subject(subject)<br>    .content(content)<br>    .status(NotificationStatus.PENDING)<br>    .build();<br>```<br>**Database:** ACID transaction ensures durability |
| 5 | `KafkaTemplate` | Publishes notification ID to Kafka topic | **Message Key:** `notification.getId().toString()`<br>**Message Value:** `notification.getId().toString()`<br>**Topic:** Channel-specific (e.g., `notifications.email`)<br>**Purpose:** Only ID sent to avoid large messages |
| 6 | **API Response** | Returns 201 Created with notification ID | **Response:**<br>```json<br>{<br>  "success": true,<br>  "message": "Notification queued successfully",<br>  "data": {<br>    "id": "550e8400-e29b-41d4-a716-446655440002",<br>    "status": "PENDING"<br>  }<br>}<br>```<br>**HTTP Status:** 201 (Created) - notification record created, delivery happens async |
| 7 | `NotificationConsumer` | Picks up message from Kafka | **Consumer Record:** `ConsumerRecord<String, String>`<br>**Value:** Notification ID string<br>**Processing:** Parse UUID, fetch from database<br>**Status Update:** `PENDING` → `PROCESSING` |
| 8 | `ChannelDispatcher` | Routes to correct handler (Email/SMS/Push/In-App) | **Routing Logic:**<br>```java<br>ChannelHandler handler = handlers.get(notification.getChannel());<br>return handler.send(notification);<br>```<br>**Strategy Pattern:** O(1) lookup via HashMap |
| 9 | `EmailChannelHandler` (etc.) | Sends via external provider (SendGrid/Twilio) | **Handler Selection:** Based on `notification.getChannel()`<br>**External API Call:** SendGrid/Twilio/Firebase/etc.<br>**Data Passed:** `user.email`, `notification.subject`, `notification.content`<br>**Return:** `true` (success) or `false` (failure) |
| 10 | `NotificationRepository` | Updates status to SENT or schedules retry | **Success:** `status = SENT`<br>**Failure:** `status = PENDING`, `retry_count++`, `next_retry_at` set with exponential backoff |

### Detailed Data Flow Example

**Client Request:**
```json
POST /api/v1/notifications
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "channel": "EMAIL",
  "templateName": "welcome-email",
  "templateVariables": {
    "userName": "John",
    "activationLink": "https://example.com/activate/abc123"
  }
}
```

**1. Controller → Service:**
- `SendNotificationRequest` object passed to `notificationService.sendNotification(request)`
- Contains: userId, channel, templateName, templateVariables

**2. Service Processing:**
- User lookup: `userService.findById(request.getUserId())` (Redis-cached, avoids DB hit per request)
- Deduplication check: `deduplicationService.isDuplicate(eventId)` (Redis key `event:{eventId}`)
- Rate limit check: Redis counter increment
- Template processing: Variables substituted in template content
- Entity creation: `Notification` object built with processed content

**3. Database Persistence:**
```sql
INSERT INTO notifications (id, user_id, channel, subject, content, status, created_at)
VALUES ('uuid', 'user-uuid', 'EMAIL', 'Welcome to Our Platform', 'Hi John, ...', 'PENDING', NOW());
```

**4. Kafka Publishing:**
- Topic: `notifications.email`
- Key: `"550e8400-e29b-41d4-a716-446655440002"`
- Value: `"550e8400-e29b-41d4-a716-446655440002"`
- Purpose: Decouple API response from slow email sending

**5. Consumer Processing:**
- Kafka message received: `"550e8400-e29b-41d4-a716-446655440002"`
- Database fetch: `notificationRepository.findById(uuid)`
- Status update: `PENDING` → `PROCESSING`

**6. Channel Dispatch:**
- Handler lookup: `handlers.get(ChannelType.EMAIL)` → `EmailChannelHandler`
- Method call: `emailHandler.send(notification)`

**7. Email Handler Execution:**
- User data: `notification.getUser().getEmail()`
- Content data: `notification.getSubject()`, `notification.getContent()`
- External API: SendGrid/Mailgun/SES integration
- Result: Success/failure boolean

**8. Final Status Update:**
- Success: `notification.markAsSent()` → `status = SENT`
- Failure: `notification.scheduleRetry("Delivery failed")` → `status = PENDING`, retry scheduled


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

### 2. Redis Caching (Spring Cache Abstraction)


**How it works:**
```java
// Service layer caching with Spring @Cacheable
@Service
public class UserService {
    
    @Cacheable(value = "users", key = "'email:' + #email")
    public User findByEmail(String email) {
        // First call hits database, result cached
        // Subsequent calls serve from Redis
        return userRepository.findByEmail(email).orElseThrow();
    }
    
    @CacheEvict(value = "users", key = "'email:' + #oldEmail")
    public void evictUserCacheByEmail(String oldEmail) {
        // Removes stale cache entry when email changes
    }
}
```

**Cache Configuration:**
```java
@Bean
public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, 
                                   JsonTypeInfo.As.PROPERTY);
    
    GenericJackson2JsonRedisSerializer serializer = 
        new GenericJackson2JsonRedisSerializer(objectMapper);
    
    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
        .serializeKeysWith(new StringRedisSerializer())
        .serializeValuesWith(serializer)
        .entryTtl(Duration.ofHours(1))  // 1 hour TTL
        .disableCachingNullValues();
        
    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(config)
        .build();
}
```

**Serialization Challenges Solved:**
- **ClassCastException:** Fixed with Jackson default typing
- **Lazy Loading:** Prevented with @JsonIgnore on JPA relationships
- **Complex Objects:** Proper type information in JSON

**Cache Keys:**
- `users::id:{id}` - User by ID lookup (used in notification hot path)
- `users::email:{email}` - User by email lookup
- `users::phone:{phone}` - User by phone lookup  
- `users::deviceTokens` - List of users with push tokens
- `templates::name:{name}` - Template by name lookup

**Why This Approach:**
- **Performance:** Reduces database load by 60-80% for cached data
- **Consistency:** Cache eviction ensures data accuracy
- **Scalability:** Redis cluster-ready for horizontal scaling
- **Simplicity:** Spring Cache abstraction hides complexity

---

### 3. Message Queue (Kafka)


**Why Kafka?**
| Without Kafka | With Kafka |
|---------------|------------|
| API waits for email to send (slow) | API returns immediately (fast) |
| If email fails, request fails | Worker retries independently |
| Can't handle traffic spikes | Queue absorbs spikes |
| Single point of failure | Decoupled, fault-tolerant |

**Key Design Decisions (Alex Xu's Pattern):**
1. **Channel-Specific Topics:** `notifications.email`, `notifications.sms`, `notifications.push`, `notifications.in-app`
   - Each channel scales independently
   - Email issues don't affect push delivery
   - Different partition counts per channel based on volume
2. **Key = Notification ID:** Ensures ordering per notification
3. **Manual Acknowledgment:** Only commit offset after successful processing
4. **Separate Consumer Groups:** Each channel has its own consumer group for independent offset tracking

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
// Retry 1: 5 min, Retry 2: 25 min, Retry 3: exceeds max → FAILED
long delayMinutes = (long) Math.pow(5, retryCount);
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

## Database Schema Design

### Entity Relationship Diagram
```
┌─────────────────┐       ┌─────────────────────┐
│     users       │       │  user_preferences   │
├─────────────────┤       ├─────────────────────┤
│ id (UUID)       │◄──────┤ id (UUID)           │
│ email (VARCHAR) │       │ user_id (FK)        │
│ phone (VARCHAR) │       │ channel (VARCHAR)   │
│ device_token    │       │ enabled (BOOLEAN)   │
│ created_at      │       │ quiet_hours_start   │
│ updated_at      │       │ quiet_hours_end     │
└─────────────────┘       └─────────────────────┘
         │                           │
         │                           │
         ▼                           │
┌─────────────────┐       ┌─────────────────────┐
│ notifications   │       │ notification_       │
├─────────────────┤       │    templates        │
│ id (UUID)       │       ├─────────────────────┤
│ user_id (FK)    │──────►│ id (UUID)           │
│ template_id (FK)│◄──────┤ name (VARCHAR)      │
│ channel         │       │ channel (VARCHAR)   │
│ priority        │       │ subject_template    │
│ subject         │       │ body_template (TEXT)│
│ content (TEXT)  │       │ is_active (BOOLEAN) │
│ status          │       └─────────────────────┘
│ retry_count     │
│ max_retries     │
│ next_retry_at   │
│ error_message   │
│ sent_at         │
│ delivered_at    │
│ read_at         │
│ created_at      │
└─────────────────┘
```

### Table Schemas

#### Users Table
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    device_token VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

#### User Preferences Table
```sql
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, channel)
);
```

#### Notification Templates Table
```sql
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL UNIQUE,
    channel VARCHAR(20) NOT NULL,
    subject_template VARCHAR(500),
    body_template TEXT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

#### Notifications Table (Main Table)
```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template_id UUID REFERENCES notification_templates(id),
    channel VARCHAR(20) NOT NULL,
    priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    subject VARCHAR(500),
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### Design Decisions & Intuition

#### Why UUID Primary Keys?
- **Globally Unique**: No collisions across distributed systems
- **No Sequence Gaps**: Don't reveal how many records exist (security)
- **Client Generation**: Can generate IDs without database round-trip
- **Microservices Ready**: Perfect for distributed architectures

#### Why Separate User Preferences?
- **Flexibility**: Each user can have different preferences per channel
- **Scalability**: Preferences change less frequently than notifications
- **Compliance**: Easy to implement "Do Not Disturb" features
- **Analytics**: Track preference patterns across user base

#### Why Template System?
- **Consistency**: Standardized messaging across the platform
- **Maintainability**: Change message content in one place
- **Localization**: Easy to support multiple languages
- **Personalization**: Template variables for dynamic content
- **Testing**: Templates can be tested independently

#### Why Comprehensive Notification Tracking?
- **Audit Trail**: Complete history of all notifications sent
- **Analytics**: Track delivery rates, user engagement, failures
- **Debugging**: Detailed error messages and retry information
- **Compliance**: Prove notifications were sent (legal requirements)
- **Business Intelligence**: Understand user behavior patterns

#### Status Flow Design
```
PENDING → PROCESSING → SENT → DELIVERED
    ↓           ↓
  FAILED     READ (in-app only)
```

- **PENDING**: Queued for processing
- **PROCESSING**: Currently being sent (prevents duplicate processing)
- **SENT**: Successfully delivered to provider (SMS gateway, email service)
- **DELIVERED**: Confirmed received by user (webhook/callback)
- **FAILED**: All retries exhausted
- **READ**: User opened/acknowledged (in-app notifications only)

#### Retry Strategy
- **Exponential Backoff**: `next_retry_at = now + 5^retryCount minutes` (5min, 25min)
- **Maximum Retries**: Default 3 attempts before marking as FAILED
- **Configurable**: Different retry counts for different priority levels
- **Smart Scheduling**: Use database indexes for efficient retry queries

#### Indexing Strategy
```sql
-- User inbox (most common query)
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);

-- Processing queue (find pending notifications)
CREATE INDEX idx_notifications_status ON notifications(status);

-- Retry scheduling (find notifications ready for retry)
CREATE INDEX idx_notifications_retry ON notifications(next_retry_at) 
    WHERE status = 'PENDING' AND next_retry_at IS NOT NULL;

-- Analytics (filter by channel/time)
CREATE INDEX idx_notifications_channel ON notifications(channel);
```

**Why These Indexes?**
- **User Inbox**: `user_id + created_at DESC` - Fast pagination for user's notification history
- **Queue Processing**: `status` - Quickly find PENDING notifications to process
- **Retry Logic**: Partial index on `next_retry_at` - Only index rows that need retry
- **Analytics**: `channel` - Group notifications by delivery method

#### Data Types Rationale
- **UUID**: Globally unique identifiers (vs auto-increment integers)
- **TIMESTAMP WITH TIME ZONE**: Proper timezone handling across regions
- **TEXT**: Unlimited message content (vs VARCHAR limits)
- **VARCHAR(500)**: Reasonable limits for subjects/tokens
- **BOOLEAN**: Simple true/false flags (vs CHAR(1) 'Y'/'N')

#### Constraints & Validation
- **UNIQUE(email)**: Prevent duplicate user accounts
- **UNIQUE(user_id, channel)**: One preference per user per channel
- **UNIQUE(name)**: Template names must be unique
- **NOT NULL**: Critical fields that must always have values
- **FOREIGN KEY**: Maintain referential integrity
- **ON DELETE CASCADE**: Clean up related data when users are deleted

### Database Performance Considerations

#### Read-Heavy Workload
- **Notification System**: 90% reads (checking preferences, templates) vs 10% writes
- **User Inbox**: Frequent queries for user's notification history
- **Analytics**: Aggregate queries on notification data

#### Write Patterns
- **Notifications**: High-volume inserts (millions per day)
- **Status Updates**: Frequent updates during processing
- **Retry Logic**: Updates to retry_count and next_retry_at

#### Optimization Strategies
- **Connection Pooling**: Reuse database connections (HikariCP)
- **Read Replicas**: Separate read traffic from writes
- **Partitioning**: Partition notifications table by month/year
- **Archiving**: Move old notifications to cold storage

### Interview Questions: Database Design

| **Question** | **Answer** |
|--------------|------------|
| **Why UUIDs?** | Global uniqueness, no sequence gaps, client generation |
| **Why separate preferences?** | User control, compliance, different settings per channel |
| **Why templates?** | Consistency, maintainability, localization support |
| **Why track everything?** | Audit trail, analytics, debugging, compliance |
| **Status flow?** | PENDING→PROCESSING→SENT→DELIVERED, with FAILED/READ |
| **Retry strategy?** | Exponential backoff, max 3 retries, configurable |
| **Indexing strategy?** | User inbox, queue processing, retry scheduling, analytics |
| **Performance?** | Read-heavy, connection pooling, read replicas, partitioning |
| **Data integrity?** | Foreign keys, constraints, cascading deletes |
| **Scalability?** | Partitioning, archiving, read replicas |


---

## Design Decisions & Trade-offs


| Decision | Why | Trade-off |
|----------|-----|-----------|
| **Kafka over RabbitMQ** | Better durability, replay capability, high throughput | More complex to operate |
| **Redis for rate limiting** | Fast (in-memory), TTL support, atomic operations | Additional infrastructure |
| **PostgreSQL** | ACID compliance, JSON support, reliability | Harder to scale horizontally |
| **UUID primary keys** | Globally unique, can generate client-side | Larger than integers, slower indexes |
| **Save-then-publish** | Notification survives Kafka failure | Possible duplicate sends |
| **Channel-specific Kafka topics** | Independent scaling, channel isolation, follows Alex Xu's design | More topics to manage |
| **Polling retry scheduler** | Simple, reliable | Less immediate than push-based |


---

# Part 3 — Performance & Operations

> *Show interviewers you think about production. This covers tuning, monitoring, and detailed data flow.*

---

## Performance Tuning & Throughput (10k req/sec)

<!--
==========================================================================
📌 SECTION PURPOSE: Explain the performance work done on this system
==========================================================================
-->

### Current Configuration (Tuned for 10k req/sec)

| Layer | Setting | Value | Why |
|-------|---------|-------|-----|
| **Tomcat** | max-threads | 400 | Handle concurrent requests (up from default 200) |
| **Tomcat** | max-connections | 10,000 | Accept 10k simultaneous connections |
| **Tomcat** | connection-timeout | 5,000ms | Fail fast instead of holding resources |
| **HikariCP** | maximum-pool-size | 50 | At 5ms/query, 50 conns = ~10,000 queries/sec |
| **HikariCP** | connection-timeout | 5,000ms | Fail fast if pool exhausted |
| **Hibernate** | batch_size | 50 | Batch inserts reduce DB round-trips |
| **Hibernate** | provider_disables_autocommit | true | Avoids extra DB call per transaction |
| **Kafka Producer** | acks | 1 | Leader-only ack (5x faster than acks=all) |
| **Kafka Producer** | linger.ms | 5 | Wait 5ms to fill batches before sending |
| **Kafka Producer** | batch-size | 64KB | Larger batches = fewer network calls |
| **Kafka Producer** | compression | lz4 | Compress messages to reduce network I/O |
| **Kafka Consumer** | concurrency | 10 | 10 parallel consumer threads |
| **Kafka Consumer** | max-poll-records | 500 | Process 500 records per poll cycle |
| **Kafka Topics** | total partitions | 26 | email:8, push:8, in-app:6, sms:4 |

### Back-of-Envelope Capacity

| Component | Per-Request Cost | Capacity |
|-----------|-----------------|----------|
| Tomcat accept | ~0ms | 10,000 conns |
| Redis (dedup + rate limit) | ~1ms (2 calls) | ~50,000/s |
| User lookup (Redis cache hit) | ~0.5ms | ~50,000/s |
| DB save (notification INSERT) | ~5ms | 50 conns / 5ms = **10,000/s** |
| Kafka send (batched, acks=1) | ~0.1ms amortized | **50,000+/s** |

**Estimated single-instance throughput: ~8,000-12,000 req/sec**

### Interview Answer: "How did you tune for 10k req/sec?"

"I identified bottlenecks layer by layer:

1. **User lookup:** Each request was hitting PostgreSQL. I added `@Cacheable` to `UserService.findById()` so user lookups go to Redis (~0.5ms vs ~5ms DB).

2. **DB connection pool:** Increased HikariCP from 10 to 50. At 5ms per query, 50 connections supports ~10k queries/sec.

3. **Kafka producer:** Changed from `acks=all` to `acks=1` (leader-only). Added batching with `linger.ms=5` and `batch-size=64KB` with lz4 compression. This makes sends effectively fire-and-forget at ~0.1ms amortized.

4. **Tomcat threads:** Increased to 400 threads with 10k max connections and 5s timeout.

5. **Logging:** Moved hot-path logs from INFO to DEBUG to avoid I/O overhead at 10k/s.

The bottleneck shifts to PostgreSQL INSERTs at ~10k/sec. Beyond that, horizontal scaling (multiple instances behind a load balancer) is needed."


---

## Monitoring & Stress Testing

<!--
==========================================================================
📌 SECTION PURPOSE: Show you built observability into the system
==========================================================================
-->

### Monitoring Stack (Implemented)

The project includes a complete **Prometheus + Grafana** monitoring setup:

```
Application (Micrometer) → Prometheus (scrape) → Grafana (dashboards)
```

**Prometheus Metrics Exposed:**
- HTTP request rate, latency percentiles (p95, p99)
- JVM heap usage, GC frequency
- HikariCP connection pool utilization
- Kafka consumer lag per topic
- Custom notification counters per channel

**Grafana Dashboard Panels:**
- HTTP throughput by endpoint
- Response time distribution
- JVM heap and GC pressure
- Database connection pool health
- Kafka consumer lag

**Configuration:** `monitoring/prometheus/prometheus.yml` scrapes the `/actuator/prometheus` endpoint.

### Stress Testing (k6)

The project includes three k6 load test profiles in `stress-test/k6/`:

| Script | Purpose | Peak VUs |
|--------|---------|----------|
| `stress-test.js` | Steady-state throughput and latency baseline | 10,000 |
| `spike-test.js` | Burst handling and recovery behavior | 1,000 |
| `heap-test.js` | JVM heap growth and GC under pressure | — |

**Thresholds defined:**
- `http_req_failed`: < 5% error rate
- `http_req_duration`: p95 < 750ms, p99 < 1200ms

**How to run:**
```bash
k6 run stress-test/k6/stress-test.js

# POST endpoint:
BASE_URL=http://localhost:8080 TARGET_PATH=/api/v1/notifications METHOD=POST k6 run stress-test/k6/stress-test.js
```


---

## End-to-End Data Lifecycle Deep Dive

### **Complete Data Transformation Flow**

**1. Request DTO → Entity Transformation:**
```java
// Input: SendNotificationRequest (JSON)
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "channel": "EMAIL",
  "templateName": "welcome-email",
  "templateVariables": {"userName": "John"},
  "priority": "HIGH"
}

// Transformation in NotificationService.sendNotification():
User user = userService.findById(request.getUserId()); // Redis-cached lookup

String subject = templateService.processTemplate(request.getTemplateName(),
    request.getTemplateVariables()).getSubject(); // Template processing

// Output: Notification Entity
Notification notification = Notification.builder()
    .id(UUID.randomUUID()) // Generated
    .user(user) // Foreign key relationship
    .channel(ChannelType.EMAIL) // Enum conversion
    .priority(Priority.HIGH) // Enum conversion
    .subject(subject) // Processed from template
    .content(content) // Processed from template
    .status(NotificationStatus.PENDING) // Initial status
    .retryCount(0) // Initial retry count
    .maxRetries(3) // Configurable
    .createdAt(OffsetDateTime.now()) // Timestamp
    .build();
```

**2. Entity → Database Record:**
```sql
-- PostgreSQL INSERT (via JPA/Hibernate)
INSERT INTO notifications (
    id, user_id, channel, priority, subject, content,
    status, retry_count, max_retries, created_at
) VALUES (
    '550e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440001',
    'EMAIL', 'HIGH',
    'Welcome to Our Platform, John!',
    'Hi John,\n\nThank you for joining...',
    'PENDING', 0, 3, '2024-01-27T10:30:00Z'
);
```

**3. Entity → Kafka Message:**
```java
// Only ID sent to Kafka (small message, decoupling)
String messageKey = notification.getId().toString();
String messageValue = notification.getId().toString();
String topic = "notifications.email"; // Channel-specific

kafkaTemplate.send(topic, messageKey, messageValue);
```

**4. Kafka Message → Consumer Processing:**
```java
// Consumer receives minimal message
ConsumerRecord<String, String> record = ...;
String notificationIdStr = record.value(); // "550e8400-e29b-41d4-a716-446655440002"

// Fetch full entity from database
UUID notificationId = UUID.fromString(notificationIdStr);
Notification notification = notificationRepository.findById(notificationId)
    .orElseThrow(() -> new RuntimeException("Notification not found"));
```

**5. Entity → Channel Handler Data:**
```java
// EmailChannelHandler.send()
String recipientEmail = notification.getUser().getEmail(); // "john@example.com"
String subject = notification.getSubject(); // "Welcome to Our Platform, John!"
String body = notification.getContent(); // Full processed content

// External API call (SendGrid example)
SendGrid sg = new SendGrid(apiKey);
Email from = new Email("noreply@company.com");
Email to = new Email(recipientEmail);
Content content = new Content("text/html", body);
Mail mail = new Mail(from, subject, to, content);
```

**6. Status Updates → Database:**
```java
// Success path
notification.setStatus(NotificationStatus.SENT);
notification.setSentAt(OffsetDateTime.now());
notificationRepository.save(notification);

// Failure path
notification.setRetryCount(notification.getRetryCount() + 1);
notification.setNextRetryAt(OffsetDateTime.now().plusMinutes(
    (long) Math.pow(5, notification.getRetryCount()))); // 5^n backoff (5min, 25min)
notification.setErrorMessage("SMTP connection failed");
notificationRepository.save(notification);
```

### **Error Handling & Failure Scenarios**

**Scenario 1: Rate Limit Exceeded**
```
Client Request → Controller → Service.checkRateLimit()
    ↓
RateLimitExceededException thrown
    ↓
GlobalExceptionHandler catches → 429 Too Many Requests
    ↓
Response: {"error": "Rate limit exceeded", "retryAfter": "3600"}
```
**Data State:** No notification record created, no Kafka message sent

**Scenario 2: Template Not Found**
```
Service.processTemplate() → TemplateNotFoundException
    ↓
Wrapped in NotificationException
    ↓
GlobalExceptionHandler → 400 Bad Request
```
**Data State:** No notification record created

**Scenario 3: Kafka Publishing Failure**
```
kafkaTemplate.send() throws exception
    ↓
Logged as error, but transaction continues
    ↓
Notification saved to DB with PENDING status
    ↓
RetryScheduler will pick it up later
```
**Data State:** Notification exists in DB, will be retried

**Scenario 4: Channel Handler Failure**
```
EmailChannelHandler.send() returns false
    ↓
Consumer calls notification.scheduleRetry()
    ↓
Status: PENDING, retry_count++, next_retry_at set
    ↓
Kafka message acknowledged (prevents infinite retry)
```
**Data State:** Notification scheduled for retry with exponential backoff

**Scenario 5: Database Connection Failure**
```
notificationRepository.save() throws exception
    ↓
@Transactional rolls back entire operation
    ↓
No notification record created
    ↓
API returns 500 Internal Server Error
```
**Data State:** Consistent state maintained via ACID transactions

### **Retry Mechanism Deep Dive**

**Exponential Backoff Schedule:**
```java
// RetryScheduler.processRetries()
List<Notification> readyNotifications = notificationRepository
    .findReadyForProcessing(OffsetDateTime.now());

// For each failed notification:
int retryCount = notification.getRetryCount();
Duration backoff = Duration.ofMinutes((long) Math.pow(5, retryCount));

// retryCount=1: 5 minutes, retryCount=2: 25 minutes, retryCount=3: FAILED (max reached)
notification.setNextRetryAt(OffsetDateTime.now().plus(backoff));
```

**Retry State Machine:**
```
PENDING → PROCESSING → SENT (success)
    ↓           ↓
   FAIL       FAIL → PENDING (retry scheduled)
                      ↓
                   Max retries exceeded → FAILED
```

**Dead Letter Queue Pattern:**
```java
// After maxRetries exceeded
if (notification.getRetryCount() >= notification.getMaxRetries()) {
    notification.setStatus(NotificationStatus.FAILED);
    notification.setErrorMessage("Max retries exceeded");
    // Could publish to dead letter topic for manual review
}
```

### **Data Consistency & Concurrency**

**Optimistic Locking (Version Fields):**
```java
// Notification entity
@Version
private Long version; // Hibernate increments on update

// Prevents concurrent modifications
notificationRepository.save(notification); // Throws exception if version mismatch
```

**Idempotency for Duplicate Prevention:**
```java
// DeduplicationService
public boolean isDuplicate(String eventId) {
    String key = "event:" + eventId;
    Boolean exists = redisTemplate.hasKey(key);
    if (Boolean.TRUE.equals(exists)) {
        return true; // Duplicate!
    }
    // Mark as seen with 24-hour TTL
    redisTemplate.opsForValue().set(key, "1",
        Duration.ofSeconds(eventTtlSeconds));
    return false;
}
```

**Transactional Boundaries:**
```java
@Transactional
public NotificationResponse sendNotification(SendNotificationRequest request) {
    // User lookup, rate limiting, template processing
    // ALL succeed or ALL fail together
    Notification saved = notificationRepository.save(notification);
    
    // Kafka publish (non-transactional)
    sendToKafka(saved);
    
    return NotificationResponse.from(saved);
}
```

### **Monitoring & Observability Data Flow**

**Metrics Collection:**
```java
// NotificationService
@Timed(value = "notification.send", histogram = true)
public NotificationResponse sendNotification(SendNotificationRequest request) {
    // Implementation
}

// Custom metrics
meterRegistry.counter("notification.sent", "channel", channel.name()).increment();
meterRegistry.timer("notification.delivery.time", "channel", channel.name())
    .record(() -> channelHandler.send(notification));
```

**Logging Data Points:**
```java
// Structured logging with MDC
MDC.put("notificationId", notification.getId().toString());
MDC.put("userId", notification.getUser().getId().toString());
MDC.put("channel", notification.getChannel().name());

log.info("Processing notification for delivery");
```

**Health Check Data:**
```java
// Actuator endpoint data
@Component
public class NotificationHealthIndicator implements HealthIndicator {
    public Health health() {
        long pendingCount = notificationRepository.countByStatus(PENDING);
        if (pendingCount > 10000) { // Threshold
            return Health.down()
                .withDetail("pendingNotifications", pendingCount)
                .build();
        }
        return Health.up().build();
    }
}
```

### **Data Retention & Cleanup**

**Notification Archival:**
```java
// Scheduled job for old notifications
@Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
@Transactional
public void archiveOldNotifications() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusMonths(6);
    List<Notification> oldNotifications = notificationRepository
        .findByCreatedAtBeforeAndStatusIn(cutoff, 
            List.of(SENT, FAILED));
    
    // Move to archive table or delete
    notificationRepository.deleteAll(oldNotifications);
}
```

**Redis Key Expiration:**
```java
// Rate limiting keys auto-expire
redisTemplate.expire(rateLimitKey, Duration.ofHours(1));

// Cache keys have TTL
@Cacheable(value = "users", key = "'email:' + #email", 
           unless = "#result == null")
public User findByEmail(String email) {
    return userRepository.findByEmail(email);
}
```

### **Edge Cases & Data Validation**

**Invalid User Scenarios:**
- User deleted between API call and consumer processing
- User email/phone changed during processing
- User preferences disable channel during processing

**Template Processing Edge Cases:**
- Missing template variables
- Malformed template syntax
- Template channel mismatch

**Concurrent Processing:**
- Multiple consumers processing same notification
- Rate limit counters updated simultaneously
- Template cache invalidation during updates

**Network & External Service Failures:**
- Kafka broker unavailable
- Email provider rate limited
- Database connection pool exhausted
- Redis cluster failover


---

# Part 4 — Interview Questions & Answers

> *31 questions with model answers. Don't memorize word-for-word — understand the key points and explain in your own words.*

---

### System Design Questions


---

**Q1: Walk me through how a notification is sent in your system.**

**Answer:**
"When a client sends a POST request to `/api/v1/notifications`:

1. The **Controller** validates the request body using Bean Validation
2. The **Service** checks for duplicate events via Redis, then checks rate limits - if exceeded, returns 429
3. If using a template, the **TemplateService** processes variable substitution
4. The notification is saved to **PostgreSQL** with PENDING status
5. The notification ID is published to **Kafka** (channel-specific topic)
6. We return **201 Created** immediately (delivery happens async)
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
- Max 3 retries with 5^n backoff (5min, 25min), then status = FAILED
- Stuck notification reset (if PROCESSING > 10 minutes, reset to PENDING)
- All failures are logged for monitoring"

---

**Q5: What happens if Kafka is down when you try to publish?**

**Answer:**
"The notification is safe because of my **save-then-publish** pattern:

1. I save to PostgreSQL first (transactional, durable)
2. Then publish to Kafka (fire-and-forget with `acks=1`)

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
   - `acks=1` (leader acknowledges before confirming)
   - Idempotent producer enabled (`enable.idempotence=true`) to prevent duplicates
   - Producer batching with retries=3 and retry.backoff.ms=1000
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
"I implemented a two-layer deduplication strategy:

**1. Event-Level Deduplication (Prevention):**
   Clients can provide an `eventId` in notification requests. Before creating any notification, I check:
   ```java
   if (request.getEventId() != null && deduplicationService.isDuplicate(request.getEventId())) {
       return NotificationResponse.builder()
           .status(FAILED)
           .errorMessage("Duplicate event: notification already processed")
           .build();
   }
   ```
   - **Redis Storage:** Event IDs stored with 24-hour TTL using keys like `event:{eventId}`
   - **Atomic Check:** Uses Redis `SET NX` semantics to prevent race conditions
   - **Distributed Safe:** Works across multiple application instances

**2. Processing-Level Deduplication (Recovery):**
   For Kafka message duplicates, I check status before processing:
   ```java
   if (notification.getStatus() != PENDING) {
       log.info("Notification {} already processed", notificationId);
       return; // Skip duplicate processing
   }
   ```

**3. Database-Level Deduplication:**
   PostgreSQL constraints prevent duplicate data insertion.

This gives me **exactly-once delivery** at the event level and **at-least-once with deduplication** at the processing level."

---

**Q14: Why did you use channel-specific Kafka topics?**

**Answer:**
"I implemented **channel-specific topics** following Alex Xu's system design pattern:

- `notifications.email` - 8 partitions (high volume, can tolerate delay)
- `notifications.sms` - 4 partitions (rate-limited by providers like Twilio)
- `notifications.push` - 8 partitions (needs to be fast for UX)
- `notifications.in-app` - 6 partitions (moderate volume)

**Benefits of this approach:**

1. **Independent Scaling:** I can have 10 email consumers but only 4 SMS consumers based on volume
2. **Fault Isolation:** If the email provider is down, push notifications still work
3. **Different SLAs:** Push notifications can have higher processing priority
4. **Better Monitoring:** Track lag and throughput per channel separately

**Implementation:**
```java
// Route to correct topic based on channel
private String getTopicForChannel(ChannelType channel) {
    return switch (channel) {
        case EMAIL -> emailTopic;
        case SMS -> smsTopic;
        case PUSH -> pushTopic;
        case IN_APP -> inAppTopic;
    };
}
```

Each channel also has its own consumer group for independent offset tracking."

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

**Q17: Tell me about your caching implementation. What do you cache and why?**

**Answer:**
"I implemented Redis caching for frequently accessed data to reduce database load:

**What I cache:**
1. **User lookups by ID:** `users::id:{id}` → User entity (hot path for notifications)
2. **User lookups by email:** `users::email:{email}` → User entity
3. **User lookups by phone:** `users::phone:{phone}` → User entity  
4. **Users with device tokens:** `users::deviceTokens` → List<User>
5. **Notification templates:** `templates::name:{name}` → TemplateResponse

**Why these specifically:**
- **User lookups by ID:** Called on every notification send; Redis cache eliminates a DB round-trip per request
- **User lookups by email/phone:** Called frequently during user operations, users don't change often
- **Device tokens:** Push notifications need to find all users with tokens, expensive query
- **Templates:** Reusable content, read-heavy, write-rare

**Cache strategy:**
- **TTL:** 1 hour for all cached data
- **Serialization:** Jackson with default typing for complex objects
- **Eviction:** @CacheEvict when data changes (user email update, template modification)
- **Cache misses:** Only successful lookups are cached, exceptions are not"

---

**Q18: How do you handle cache consistency when data changes?**

**Answer:**
"I use cache eviction strategies:

**For user data:**
```java
@CacheEvict(value = "users", key = "'email:' + #oldEmail")
public void evictUserCacheByEmail(String oldEmail) {
    // Removes stale cache entry
}
```

**For templates:**
```java
@CacheEvict(value = "templates", allEntries = true)
public TemplateResponse updateTemplate(...) {
    // Clears all template cache on any change
}
```

**Why this approach:**
- **Immediate consistency:** Cache is invalidated when data changes
- **Simple:** No complex cache update logic
- **Safe:** Next request will hit database and refresh cache
- **Performance:** Better than cache invalidation storms"

---

**Q19: What serialization challenges did you face with Redis caching?**

**Answer:**
"Two main issues:

**1. ClassCastException with LinkedHashMap:**
- **Problem:** Jackson deserialized User objects as LinkedHashMap
- **Root cause:** Missing type information in JSON
- **Solution:** Enabled default typing in ObjectMapper:
```java
objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
```

**2. Lazy loading exceptions:**
- **Problem:** @OneToMany relationships caused lazy loading in cached objects
- **Root cause:** JPA proxy objects can't be serialized
- **Solution:** Added @JsonIgnore to lazy relationships:
```java
@OneToMany(fetch = FetchType.LAZY)
@JsonIgnore
private List<Notification> notifications;
```

**Result:** Clean JSON serialization without database dependencies"

---

**Q20: How do you test your caching implementation?**

**Answer:**
"I test both cache hits and cache misses:

**Cache Miss Testing:**
```bash
# Clear cache
docker exec notification-redis redis-cli FLUSHALL

# First request - hits database
http :8080/api/v1/users/email/john@example.com
# Logs show: Hibernate SQL query executed

# Check Redis - data is now cached
docker exec notification-redis redis-cli keys "*"
# Shows: users::email:john@example.com

# Second request - serves from cache
http :8080/api/v1/users/email/john@example.com  
# No database query in logs
```

**Failed Lookup Testing:**
```bash
# Non-existent user
http :8080/api/v1/users/email/nonexistent@example.com
# Returns 404, no cache entry created
# Redis keys still shows only successful lookups
```

**Cache Eviction Testing:**
```bash
# Update user email (would trigger @CacheEvict)
# Verify old cache key is removed, new one is created
```"

---

### API Design Questions


---

**Q21: Why return 201 Created instead of 200 OK?**

**Answer:**
"HTTP 201 means 'a new resource was created as a result of the request.'

This is semantically correct because:
- A notification record is created in the database
- The notification ID is returned so clients can track it
- Actual delivery happens asynchronously via Kafka

200 OK would also be acceptable, but 201 better communicates that a new resource (the notification) was created.

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

**Q22: How do you handle validation errors?**

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

**Q23: How do you handle partial failures in bulk notifications?**

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

**Q24: What monitoring did you implement for production readiness?**

**Answer:**
"I built a full observability stack into the project:

1. **Metrics (Prometheus + Micrometer) — implemented:**
   - Spring Boot Actuator exposes `/actuator/prometheus`
   - Prometheus scrapes metrics every 15s (configured in `monitoring/prometheus/prometheus.yml`)
   - Grafana dashboards visualize HTTP throughput, JVM heap, DB pool health, and Kafka consumer lag
   - Custom counters for notifications sent per channel, rate limit rejections, retry counts

2. **Stress Testing (k6) — implemented:**
   - `stress-test.js`: 10,000 VU ramp-up to validate throughput target
   - `spike-test.js`: Sudden burst to test circuit-breaker behavior
   - `heap-test.js`: Long-running load to detect JVM memory leaks
   - Thresholds: p95 < 750ms, error rate < 5%

3. **Health endpoints (Spring Boot Actuator) — implemented:**
   - `/health` - Overall health with DB, Redis, Kafka checks
   - `/actuator/prometheus` - Prometheus-format metrics

4. **Logging:**
   - Hot-path logs set to DEBUG to avoid I/O overhead at scale
   - WARN level for errors and anomalies
   - Structured logging ready for ELK stack integration

5. **Alerting (Grafana — ready to configure):**
   - Failed notification rate > 5%
   - Kafka consumer lag > 1000
   - HikariCP pool utilization > 80%
   - Redis unavailable"

---

### Scaling & Performance Questions


---

**Q25: How would you scale this system to handle 10x traffic?**

**Answer:**
"I would scale horizontally at multiple layers:

1. **API Layer:**
   - Run multiple instances behind a load balancer
   - They're stateless, so easy to scale
   - Tomcat tuned: 400 threads, 10k max connections

2. **Kafka:**
   - Already configured with high partition counts (email:8, push:8, in-app:6, sms:4)
   - More partitions = more parallel consumers
   - Producer batching: linger.ms=5, batch-size=64KB, lz4 compression

3. **Workers:**
   - Add more consumer instances (same group ID)
   - Currently 10 concurrent consumer threads
   - Kafka distributes partitions among them

4. **Database:**
   - HikariCP pool at 50 connections with batch_size=50
   - Read replicas for inbox queries
   - Consider sharding by user_id for extreme scale

5. **Redis:**
   - User lookups cached by ID (avoids DB hit per notification)
   - Redis Cluster for distributed rate limiting
   - Consistent hashing for key distribution"

---

**Q26: What's the bottleneck in your current design?**

**Answer:**
"The most likely bottlenecks:

1. **Database writes:** Every notification = one INSERT. At high volume, this could slow down.
   **Current mitigation:** HikariCP pool=50, Hibernate batch_size=50, provider_disables_autocommit.
   **Further solution:** Batch inserts, or use a write-optimized DB like Cassandra.

2. **External providers:** SendGrid/Twilio have their own rate limits.
   **Solution:** Respect their limits, queue excess, retry with backoff.

3. **Kafka producer throughput:** Sync sends would bottleneck at high volume.
   **Current mitigation:** Fire-and-forget sends with producer batching (linger.ms=5, batch-size=64KB, lz4 compression, acks=1).

With current tuning (50 DB conns, 400 Tomcat threads, 10 Kafka consumer threads), a single instance can sustain ~8,000-12,000 req/sec. Beyond that, horizontal scaling is needed."

---

### Code Quality & Testing Questions


---

**Q27: How do you test this system?**

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

**Q28: How do you handle configuration across environments?**

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

**Q29: What was the most challenging part of building this?**

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

**Q30: What would you do differently if you rebuilt this?**

**Answer:**
"A few things:

1. **Event Sourcing:** Store notification events instead of just current state. Enables audit trails and replay.

2. **Dead Letter Queue:** Instead of max retries → FAILED, move to a DLQ for manual investigation.

3. **Circuit Breaker:** If SendGrid is down, stop trying and fail fast. Prevent cascade failures.

4. **Outbox Pattern:** Instead of publish after save, use a transactional outbox for guaranteed delivery.

These are production-grade improvements I'd add in a real system."

---

**Q31: How did you decide what to include vs. exclude?**

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

# Part 5 — AWS Production Deployment

> *Advanced topic for principal/staff-level interviews. Shows you can plan a multi-region production deployment.*

---

## AWS Production Deployment (Multi-Country Scenario)

### Global Architecture Overview
```
┌─────────────────────────────────────────────────────────────────┐
│                    GLOBAL SERVICES (Single Region)              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │   Route 53      │  │   CloudFront    │  │   WAF & Shield  │   │
│  │   (DNS)         │  │   (CDN)         │  │   (Security)     │   │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    REGIONAL SERVICES (Per Region)               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │   Application   │  │   ElastiCache   │  │   Aurora Global  │   │
│  │   Load Balancer │  │   (Redis)       │  │   Database       │   │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │   ECS/EKS       │  │   MSK (Kafka)   │  │   S3             │   │
│  │   (Containers)  │  │   (Messaging)   │  │   (Storage)      │   │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### AWS Service Mapping

| **Component** | **AWS Service** | **Configuration** | **Multi-Region** |
|---------------|-----------------|-------------------|------------------|
| **Application** | ECS Fargate / EKS | Auto-scaling groups, health checks | Regional deployment |
| **Database** | Aurora Global Database | PostgreSQL 15, multi-master | Global write/read endpoints |
| **Cache** | ElastiCache Global Datastore | Redis 7, cluster mode | Global replication |
| **Message Queue** | Amazon MSK | Kafka 3.x, multi-AZ | Regional with cross-region replication |
| **Load Balancer** | Application Load Balancer | SSL termination, WAF integration | Regional |
| **CDN** | CloudFront | Edge locations, caching rules | Global |
| **DNS** | Route 53 | Geo-routing, health checks | Global |
| **Storage** | S3 | Versioning, cross-region replication | Multi-region replication |
| **Security** | AWS WAF + Shield | Rate limiting, DDoS protection | Global |
| **Monitoring** | CloudWatch + X-Ray | Metrics, logs, tracing | Cross-region aggregation |

### Multi-Region Deployment Strategy

#### Primary-Secondary Model
```
US-East-1 (Primary)     EU-West-1 (Secondary)     AP-Southeast-1 (Secondary)
├── Aurora Writer       ├── Aurora Reader         ├── Aurora Reader
├── Redis Primary       ├── Redis Replica         ├── Redis Replica
├── MSK Primary         ├── MSK Replica           ├── MSK Replica
└── ECS Tasks           └── ECS Tasks             └── ECS Tasks
```

#### Active-Active Model (Advanced)
```
US-East-1               EU-West-1                AP-Southeast-1
├── Aurora Multi-Master ├── Aurora Multi-Master  ├── Aurora Multi-Master
├── Redis Global        ├── Redis Global         ├── Redis Global
├── MSK Global          ├── MSK Global           ├── MSK Global
└── ECS Global          └── ECS Global           └── ECS Global
```

### Compliance & Data Residency

#### GDPR (Europe)
- **Data Location**: EU-West-1 (Ireland) primary
- **Data Processing**: Consent management, right to erasure
- **Cross-Border**: Explicit user consent required
- **Retention**: Configurable per regulation requirements

#### CCPA (California)
- **Data Location**: US-West-2 (Oregon) primary
- **Data Subject Rights**: Access, delete, opt-out
- **Data Mapping**: Track all personal data flows
- **Breach Notification**: <72 hours requirement

#### PDPA (Singapore/Asia)
- **Data Location**: AP-Southeast-1 (Singapore) primary
- **Data Protection**: Consent-based collection
- **Cross-Border**: PDPC approval for transfers
- **Retention**: Business necessity principle

### Cost Optimization

#### Compute (ECS/EKS)
- **Spot Instances**: 70% savings for non-critical workloads
- **Auto-scaling**: Scale to zero during low traffic
- **Graviton Processors**: 20% cost reduction vs x86

#### Database (Aurora)
- **Serverless v2**: Pay per usage, auto-scaling
- **Aurora Optimized Reads**: Up to 8x faster queries
- **Storage Auto-scaling**: No over-provisioning

#### Cache (ElastiCache)
- **Reserved Nodes**: 50% savings for predictable workloads
- **Data Tiering**: Automatic cost optimization
- **Cluster Mode**: Better memory utilization

#### Network (CloudFront)
- **Edge Locations**: Reduced latency, lower data transfer costs
- **Compression**: Gzip/Brotli for smaller payloads
- **Caching**: Reduce origin requests by 80%

### Monitoring & Observability

#### Application Metrics
```
CloudWatch Metrics:
├── Application: Response time, error rates, throughput
├── Database: Connection count, query latency, deadlocks
├── Cache: Hit rate, memory usage, eviction count
└── Queue: Message count, consumer lag, throughput
```

#### Distributed Tracing
```
X-Ray Integration:
├── Service mesh tracing across regions
├── Performance bottleneck identification
├── Error root cause analysis
└── User journey tracking
```

#### Alerting Strategy
```
Critical Alerts (Page immediately):
├── Service unavailable (>5 minutes)
├── Database connection failures
├── Queue backlog > 1M messages
└── Security incidents

Warning Alerts (Monitor trends):
├── High latency (>500ms p95)
├── Error rate > 1%
├── Cache hit rate < 80%
└── Storage utilization > 80%
```

### Disaster Recovery

#### RTO/RPO Targets
- **Critical Services**: RTO < 1 hour, RPO < 5 minutes
- **Standard Services**: RTO < 4 hours, RPO < 1 hour
- **Data Services**: RTO < 2 hours, RPO < 15 minutes

#### Failover Strategy
```
Automatic Failover:
├── Route 53 health checks trigger DNS failover
├── Aurora Global Database promotes secondary region
├── ElastiCache Global Datastore switches primary
└── ECS services scale up in secondary region
```

### Security Considerations

#### Network Security
```
VPC Design:
├── Public subnets: Load balancers only
├── Private subnets: Application and data layers
├── Isolated subnets: Database and cache
└── Transit Gateway: Cross-region connectivity
```

#### Data Protection
```
Encryption:
├── Data at rest: AWS KMS with customer keys
├── Data in transit: TLS 1.3 minimum
├── Database: Transparent Data Encryption (TDE)
└── Cache: Redis encryption in transit/at rest
```

#### Access Control
```
IAM Strategy:
├── Least privilege principle
├── Service roles for ECS tasks
├── Cross-account access via IAM roles
└── Multi-factor authentication required
```

### Performance Optimization

#### Global Latency Reduction
- **CloudFront**: 200+ edge locations worldwide
- **Route 53**: Geo-based routing to nearest region
- **Global Accelerator**: TCP/UDP optimization
- **Aurora Global Database**: Sub-second cross-region replication

#### Scaling Strategies
```
Horizontal Scaling:
├── ECS: Auto-scaling based on CPU/memory
├── Aurora: Auto-scaling read replicas
├── ElastiCache: Cluster scaling
└── MSK: Broker scaling

Vertical Scaling:
├── Instance types: Graviton3 for cost/performance
├── Storage: Aurora I/O optimization
├── Network: Enhanced networking
└── Memory: Redis cluster mode
```

### Cost Estimation (Monthly)

#### Base Infrastructure (3 Regions)
```
Compute (ECS Fargate):     $12,000 - $25,000
Database (Aurora Global):   $8,000 - $15,000
Cache (ElastiCache):        $3,000 - $6,000
Message Queue (MSK):        $2,000 - $4,000
Storage (S3):               $500 - $1,000
CDN (CloudFront):           $2,000 - $4,000
Monitoring (CloudWatch):    $800 - $1,500
Security (WAF/Shield):      $1,000 - $2,000
─────────────────────────────────────────────
Total Estimate:            $29,300 - $58,500
```

#### Traffic-Based Scaling
- **1M daily notifications**: +20% infrastructure cost
- **10M daily notifications**: +100% infrastructure cost
- **100M daily notifications**: +300% infrastructure cost

### Interview Questions: AWS Production Deployment

| **Question** | **Answer** |
|--------------|------------|
| **Why multi-region?** | Compliance, latency, disaster recovery |
| **Database choice?** | Aurora Global - cross-region replication, PostgreSQL compatibility |
| **Cache strategy?** | ElastiCache Global - worldwide replication, Redis 7 |
| **CDN choice?** | CloudFront - 200+ locations, WAF integration |
| **DNS routing?** | Route 53 geo-routing, health-based failover |
| **Security layers?** | WAF + Shield (edge), Security Groups (VPC), IAM (access) |
| **Monitoring stack?** | CloudWatch + X-Ray, centralized logging |
| **Cost optimization?** | Reserved instances, spot instances, auto-scaling |
| **Disaster recovery?** | RTO < 1hr, RPO < 5min for critical services |
| **Compliance handling?** | Regional data residency, consent management |

---

*Good luck with your interviews!*
