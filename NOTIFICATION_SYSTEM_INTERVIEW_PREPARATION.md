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

<!--
==========================================================================
📌 SECTION PURPOSE: Your "hook" for behavioral interviews
==========================================================================

WHEN TO USE:
- "Tell me about yourself"
- "Tell me about a project you've worked on"
- "What's the most interesting thing you've built?"

TIPS FOR DELIVERY:
1. Practice until you can say it naturally (not memorized-sounding)
2. Pause briefly between bullet points for emphasis
3. End with the flow diagram - it shows you understand the full picture
4. Be ready to dive deeper into ANY part they ask about

KEY BUZZWORDS INCLUDED:
- Alex Xu (shows you study system design)
- Asynchronous processing (modern architecture)
- Token Bucket algorithm (shows algorithm knowledge)
- Exponential backoff (reliability pattern)
- SOLID principles (clean code awareness)
==========================================================================
-->

> **Use this when asked: "Tell me about a project you've worked on"**

*I built a multi-channel notification system following system design principles. It sends notifications via Email, SMS, Push, and In-App channels.*

*The key technical highlights are:*
- *Asynchronous processing using Kafka for decoupling and reliability*
- *Rate limiting with Redis using the Token Bucket algorithm*
- *Template system for reusable message content*
- *Retry mechanism with exponential backoff for failed deliveries*
- *Clean layered architecture following SOLID principles*

*The system handles the full lifecycle: API receives request → validates → saves to PostgreSQL → publishes to Kafka → worker consumes and delivers via channel handlers → updates status with retry on failure."*

---

## Project Architecture Overview

<!--
==========================================================================
📌 SECTION PURPOSE: Visual understanding of system components
==========================================================================

WHY THIS MATTERS IN INTERVIEWS:
- Interviewers often ask you to "draw the architecture"
- Shows you understand how components connect
- Demonstrates you can think at a system level, not just code level

KEY CONCEPTS TO EMPHASIZE:
1. LAYERED ARCHITECTURE: Controller → Service → Repository (separation of concerns)
2. ASYNC PROCESSING: API returns fast, Kafka handles slow work
3. WORKER PATTERN: Consumer processes messages independently
4. STRATEGY PATTERN: ChannelDispatcher routes to correct handler

WHEN WHITEBOARDING:
- Start with the client on the left
- Draw the main service box in the middle
- Show Kafka as the bridge to async processing
- End with the channel handlers on the right
- Use arrows to show data flow direction
==========================================================================
-->

### High-Level Flow

```
┌─────────────┐     ┌─────────────────────────────────────────────┐     ┌─────────────┐
│   Client    │────▶│         NOTIFICATION SERVICE                │────▶│  PostgreSQL │
│  (REST API) │     │                                             │     │  (Storage)  │
└─────────────┘     │  ┌───────────┐   ┌───────────┐   ┌───────┐  │     └─────────────┘
                    │  │Controller │──▶│  Service  │──▶│  Repo │  │
                    │  └───────────┘   └─────┬─────┘   └───────┘  │     ┌─────────────┐
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
| 2 | `NotificationService` | Checks rate limit via Redis | **Rate Limit Check:** `rateLimiterService.checkAndIncrement(userId, channel)`<br>**Redis Key:** `"ratelimit:{userId}:{channel}"`<br>**Throws:** `RateLimitExceededException` if limit exceeded |
| 3 | `TemplateService` | Processes template (if using one) | **Input:** `templateName`, `templateVariables`<br>**Processing:** Variable substitution in template content<br>**Output:** `subject`, `content` strings<br>**Example:** Template `"Welcome {{userName}}!"` → `"Welcome John!"` |
| 4 | `NotificationRepository` | Saves notification with PENDING status | **Entity Creation:**<br>```java<br>Notification notification = Notification.builder()<br>    .user(user)<br>    .channel(request.getChannel())<br>    .priority(request.getPriority())<br>    .subject(subject)<br>    .content(content)<br>    .status(NotificationStatus.PENDING)<br>    .build();<br>```<br>**Database:** ACID transaction ensures durability |
| 5 | `KafkaTemplate` | Publishes notification ID to Kafka topic | **Message Key:** `notification.getId().toString()`<br>**Message Value:** `notification.getId().toString()`<br>**Topic:** Channel-specific (e.g., `notifications.email`)<br>**Purpose:** Only ID sent to avoid large messages |
| 6 | **API Response** | Returns 202 Accepted with notification ID | **Response:**<br>```json<br>{<br>  "success": true,<br>  "message": "Notification queued successfully",<br>  "data": {<br>    "id": "550e8400-e29b-41d4-a716-446655440002",<br>    "status": "PENDING"<br>  }<br>}<br>```<br>**HTTP Status:** 202 (Accepted) - async processing |
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
- User lookup: `userRepository.findById(request.getUserId())`
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

<!--
==========================================================================
📌 SECTION PURPOSE: Technical depth for follow-up questions
==========================================================================

WHEN INTERVIEWERS ASK:
- "How does X work under the hood?"
- "Why did you choose this approach?"
- "What happens when Y fails?"

EACH COMPONENT COVERS:
1. HOW it works (with diagrams)
2. WHY you chose this approach
3. KEY CODE snippets (memorize these!)
4. TRADE-OFFS you accepted

PRO TIP: Know these components inside-out. Interviewers love to 
pick one and go deep. If you can explain Rate Limiter or Retry 
Mechanism in detail, you'll stand out.
==========================================================================
-->

### 1. Rate Limiter (Token Bucket Algorithm)

<!--
🔑 INTERVIEW GOLD: Rate limiting is asked in 80% of system design interviews!

ALTERNATIVE ALGORITHMS (know these for comparison):
- Fixed Window: Simple but has boundary burst problem
- Sliding Window Log: Accurate but memory-intensive
- Sliding Window Counter: Good balance (what we use)
- Token Bucket: Allows bursts, simple to implement ← OUR CHOICE
- Leaky Bucket: Smooths out bursts, constant rate

WHY TOKEN BUCKET FOR US:
1. We WANT to allow bursts (bulk notifications are valid)
2. Simple to implement with Redis INCR + TTL
3. Self-resets without cleanup jobs

COMMON FOLLOW-UP QUESTIONS:
- "What if Redis goes down?" → Fail open (allow) or fail closed (deny)
- "How do you handle distributed rate limiting?" → Redis is shared across instances
- "What about race conditions?" → Redis INCR is atomic
-->

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

<!--
🔑 INTERVIEW GOLD: Async processing is a MUST-KNOW for SDE-2 and above!

WHY ASYNC MATTERS:
- Users don't wait for slow operations (emails take 2-5 seconds)
- System stays responsive under load
- Failures are isolated (one bad email doesn't crash the API)

KAFKA vs RABBITMQ (common interview question):
┌─────────────────┬──────────────────┬──────────────────┐
│    Feature      │      Kafka       │    RabbitMQ      │
├─────────────────┼──────────────────┼──────────────────┤
│ Throughput      │ Millions/sec     │ Thousands/sec    │
│ Durability      │ Disk by default  │ Optional         │
│ Message Replay  │ Yes (offset)     │ No               │
│ Ordering        │ Per partition    │ Per queue        │
│ Complexity      │ Higher           │ Lower            │
│ Use Case        │ Event streaming  │ Task queues      │
└─────────────────┴──────────────────┴──────────────────┘

ALEX XU'S PATTERN (we implemented this!):
- Separate topic per channel → Independent scaling
- Each channel can have different: partition count, retention, consumer count
-->

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

<!--
🔑 INTERVIEW GOLD: Reliability and fault tolerance are key SDE-2 topics!

WHY EXPONENTIAL BACKOFF:
- Immediate retry floods failing service
- Exponential backoff gives service time to recover
- Industry standard: AWS, Google, Stripe all use this

RETRY MATH:
- Retry 1: 1 minute (2^0)
- Retry 2: 2 minutes (2^1)  
- Retry 3: 4 minutes (2^2)
- Retry 4: 8 minutes (2^3)
- Retry 5: 16 minutes (2^4) → Then FAIL permanently

TOTAL WAIT: 31 minutes before giving up

COMMON FOLLOW-UP QUESTIONS:
- "Why not retry immediately?" → Would overwhelm failing service
- "What if retries also fail?" → Max retries then FAILED status
- "How do you prevent duplicate sends?" → Idempotent processing, check status before sending
- "What about stuck notifications?" → RetryScheduler resets PROCESSING > 10 min old

TWO LAYERS OF RETRY:
1. Kafka Consumer (immediate) → Fast retry for transient failures
2. RetryScheduler (cron job) → Catches anything that slipped through
-->

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

<!--
🔑 INTERVIEW GOLD: Design patterns show you write maintainable code!

STRATEGY PATTERN EXPLAINED:
- Define a family of algorithms (send via Email, SMS, Push, In-App)
- Make them interchangeable (same interface, different implementations)
- Select algorithm at runtime (based on notification.channel)

SOLID PRINCIPLES DEMONSTRATED:
- S: Single Responsibility → Each handler does one thing
- O: Open/Closed → Add new channel without changing existing code
- L: Liskov Substitution → Any handler can replace another
- I: Interface Segregation → ChannelHandler interface is minimal
- D: Dependency Inversion → Dispatcher depends on interface, not implementations

WHY O(1) DISPATCH MATTERS:
- HashMap lookup vs if-else chain
- if-else: O(n) - checks each condition
- HashMap: O(1) - direct lookup
- With 4 channels doesn't matter, with 20+ channels it does!

COMMON FOLLOW-UP QUESTIONS:
- "How would you add WhatsApp?" → Create WhatsAppChannelHandler implements ChannelHandler
- "What if a handler is slow?" → Each channel has its own Kafka topic, doesn't block others
- "How do you test handlers?" → Mock the external provider, verify send() was called
-->

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

<!--
==========================================================================
📌 SECTION PURPOSE: Show you think critically about technology choices
==========================================================================

WHY THIS MATTERS:
- Senior engineers don't just code - they make decisions
- Every choice has pros and cons
- Interviewers want to see you can justify YOUR choices

HOW TO USE THIS TABLE:
1. Memorize at least 3 decisions with their trade-offs
2. When asked "why X?", state the WHY first, then acknowledge the trade-off
3. Show you considered alternatives

EXAMPLE RESPONSE:
"I chose Kafka over RabbitMQ because I needed durability and replay 
capability. The trade-off is operational complexity, but for a 
notification system where losing messages is unacceptable, it's worth it."

PRO TIP: If interviewer pushes back on a decision, don't get defensive!
Say: "That's a valid point. In a different context with [X constraint], 
I would consider [alternative]."
==========================================================================
-->

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

## Interview Questions & Answers

<!--
==========================================================================
📌 SECTION PURPOSE: Prepared answers for common questions
==========================================================================

HOW TO USE:
1. DON'T memorize word-for-word (sounds robotic)
2. DO understand the KEY POINTS in each answer
3. Practice explaining in YOUR OWN WORDS
4. Have 2-3 specific details ready for each topic

QUESTION CATEGORIES:
- System Design: Architecture, flow, scalability
- Design Patterns: Strategy, Repository, Factory
- Database: Schema, indexes, queries
- Kafka: Topics, consumers, reliability
- Redis: Caching, rate limiting
- API: REST design, error handling
- Reliability: Retries, failures, monitoring
- Scaling: Horizontal, vertical, bottlenecks

PRO TIP: After answering, PAUSE. Let interviewer ask follow-up.
Don't over-explain or you'll run out of things to say!
==========================================================================
-->

### System Design Questions

<!--
🎯 SYSTEM DESIGN QUESTIONS: Most important for SDE-2!

What they're testing:
- Can you design systems, not just write code?
- Do you understand distributed systems concepts?
- Can you explain complex systems simply?

Key points to hit:
1. Start with requirements (functional & non-functional)
2. Draw high-level architecture first
3. Dive deep into components when asked
4. Always mention trade-offs
-->

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

<!--
🎯 DESIGN PATTERN QUESTIONS: Shows you write maintainable code!

What they're testing:
- Do you know common design patterns?
- Can you apply them appropriately?
- Do you understand SOLID principles?

PATTERNS TO KNOW COLD:
1. Strategy → ChannelHandler (most important for this project)
2. Repository → Database abstraction
3. Builder → Object construction
4. Factory → ChannelDispatcher
5. Observer → Event-driven (Kafka pub/sub)

PRO TIP: When explaining patterns, always tie to SOLID:
- Strategy → Open/Closed Principle
- Repository → Dependency Inversion
- Factory → Single Responsibility
-->

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

<!--
🎯 DATABASE QUESTIONS: Critical for any backend role!

What they're testing:
- Can you design schemas that scale?
- Do you understand indexing?
- Do you know query optimization?

KEY TOPICS:
1. Schema design (normalization, denormalization)
2. Indexing strategy (when to add, trade-offs)
3. UUID vs auto-increment
4. Partitioning for scale
5. Query optimization (EXPLAIN ANALYZE)

REMEMBER THE INDEXES:
- idx_notifications_user_id → For inbox queries
- idx_notifications_status → For worker picking up PENDING
- idx_notifications_user_created → For pagination (composite index!)
-->

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

<!--
🎯 KAFKA QUESTIONS: Hot topic for distributed systems!

What they're testing:
- Do you understand message queue concepts?
- Can you handle failures gracefully?
- Do you know Kafka-specific patterns?

KEY CONCEPTS:
1. Topics & Partitions (parallelism)
2. Consumer Groups (scaling)
3. Offsets (message tracking)
4. At-least-once vs exactly-once delivery
5. Idempotent consumers (handling duplicates)

COMMON GOTCHAS:
- "What if Kafka is down?" → Save-then-publish pattern
- "What about duplicates?" → Idempotent processing
- "How do you scale?" → More partitions + more consumers
- "Message ordering?" → Ordering per partition, not across
-->

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

**Q14: Why did you use channel-specific Kafka topics?**

**Answer:**
"I implemented **channel-specific topics** following Alex Xu's system design pattern:

- `notifications.email` - 3 partitions (high volume, can tolerate delay)
- `notifications.sms` - 2 partitions (rate-limited by providers like Twilio)
- `notifications.push` - 4 partitions (needs to be fast for UX)
- `notifications.in-app` - 3 partitions (moderate volume)

**Benefits of this approach:**

1. **Independent Scaling:** I can have 10 email consumers but only 2 SMS consumers based on volume
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

<!--
🎯 REDIS QUESTIONS: Every backend system uses Redis!

What they're testing:
- Do you understand caching patterns?
- Can you design for distributed systems?
- Do you know Redis-specific features?

KEY CONCEPTS:
1. Cache-aside pattern (check cache, then DB)
2. TTL for expiration
3. Atomic operations (INCR, SETNX)
4. Redis as rate limiter
5. Redis cluster for HA

OUR USE CASES:
1. Rate limiting (INCR + TTL)
2. Could add: Template caching
3. Could add: User preference caching
4. Could add: Session storage
-->

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

<!--
🎯 API DESIGN QUESTIONS: Foundation of backend development!

What they're testing:
- Do you follow REST best practices?
- Do you understand HTTP semantics?
- Can you design intuitive APIs?

KEY CONCEPTS:
1. HTTP status codes (200, 201, 202, 400, 401, 404, 429, 500)
2. Resource naming (nouns, not verbs)
3. Versioning (v1 in URL)
4. Pagination (offset vs cursor)
5. Error response format

OUR API HIGHLIGHTS:
- 202 Accepted for async operations
- Consistent ApiResponse wrapper
- Pagination for inbox endpoint
- Proper validation with Bean Validation
-->

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

<!--
🎯 RELIABILITY QUESTIONS: Shows you build production-ready systems!

What they're testing:
- Do you think about failure cases?
- Can you design resilient systems?
- Do you understand distributed systems challenges?

KEY CONCEPTS:
1. Graceful degradation (system works with reduced functionality)
2. Fail-fast vs fail-safe
3. Bulkhead pattern (isolate failures)
4. Circuit breaker (stop cascading failures)
5. Monitoring and alerting

MENTAL MODEL FOR FAILURES:
- What if DB is down? → Return error, Kafka still has message
- What if Kafka is down? → Notification saved in DB, retry job picks it up
- What if Redis is down? → Fail open (allow) or fail closed (deny)
- What if provider is down? → Exponential backoff, max retries, then FAILED
-->

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

<!--
🎯 SCALING QUESTIONS: Critical for SDE-2 and above!

What they're testing:
- Can you identify bottlenecks?
- Do you understand horizontal vs vertical scaling?
- Can you design for growth?

SCALING STRATEGY BY LAYER:
┌───────────┬─────────────────┬───────────────────────┐
│   Layer   │    How to Scale   │       Notes             │
├───────────┼─────────────────┼───────────────────────┤
│ API       │ More instances     │ Stateless, easy         │
│ Kafka     │ More partitions    │ Enables more consumers  │
│ Workers   │ More consumers     │ Same group ID           │
│ Database  │ Read replicas      │ For inbox queries       │
│ Redis     │ Redis Cluster      │ Consistent hashing      │
└───────────┴─────────────────┴───────────────────────┘

BOTTLENECK ANALYSIS:
1. First bottleneck: Usually database writes
2. Second bottleneck: External provider rate limits
3. Third bottleneck: Kafka partition count
-->

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

<!--
🎯 TESTING QUESTIONS: Shows you write maintainable code!

What they're testing:
- Do you write tests?
- Do you understand different test levels?
- Can you design testable code?

TEST PYRAMID:
        /\           E2E Tests (few, slow, expensive)
       /  \
      /----\        Integration Tests (some)
     /      \
    /--------\     Unit Tests (many, fast, cheap)

OUR TESTS:
1. Unit: RateLimiterServiceTest, NotificationServiceTest
2. Integration: Full Spring context with Docker DB
3. Manual: Swagger UI, Postman collection

TESTABILITY DESIGN:
- Constructor injection → Easy to pass mocks
- Interface-based → Can mock dependencies
- Single responsibility → Smaller units to test
-->

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

<!--
🎯 BEHAVIORAL QUESTIONS: They're evaluating YOU, not just your code!

What they're testing:
- How do you approach problems?
- Can you learn from mistakes?
- Do you make good trade-off decisions?

STAR METHOD:
- Situation: Set the context
- Task: What was your responsibility?
- Action: What did YOU do?
- Result: What was the outcome?

PRO TIPS:
1. Be honest about challenges (shows self-awareness)
2. Explain your REASONING, not just actions
3. Show what you LEARNED
4. Acknowledge what you'd do DIFFERENTLY

RED FLAGS TO AVOID:
- Blaming others
- Saying "we" when you mean "I"
- Over-engineering explanations
- Claiming everything was perfect
-->

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

<!--
==========================================================================
📌 SECTION PURPOSE: Step-by-step guide for whiteboard interviews
==========================================================================

TIMING (typically 45-60 min interview):
- Requirements: 2-3 min (DON'T SKIP THIS!)
- High-level design: 5-7 min
- Deep dive: 15-20 min
- Trade-offs: 5 min
- Questions for them: 5 min

COMMON MISTAKES:
1. Jumping straight to solution (ask requirements first!)
2. Going too deep too fast (start high-level)
3. Not drawing (visuals help communication)
4. Ignoring interviewer hints (they're guiding you)
5. Not discussing trade-offs (shows maturity)

WHAT TO DRAW FIRST:
1. Client/User on the left
2. Your service in the middle
3. Databases/caches on the right
4. Arrows showing data flow
5. Add detail as you discuss each component
==========================================================================
-->

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

<!--
==========================================================================
📌 SECTION PURPOSE: Vocabulary that impresses interviewers
==========================================================================

WHY KEYWORDS MATTER:
- Shows you speak the language of senior engineers
- Demonstrates breadth of knowledge
- Triggers follow-up questions you're prepared for

HOW TO USE:
- Sprinkle naturally, don't force
- Be ready to explain any term you use
- Use the right term for the context

EXAMPLE:
"I used the Strategy Pattern for channel handlers, which follows the 
Open/Closed Principle - the system is open for extension but closed 
for modification."

TERMS TO AVOID (unless you can explain deeply):
- Microservices (unless you have actual distributed services)
- Event Sourcing (unless you implemented it)
- CQRS (unless you have separate read/write models)
==========================================================================
-->

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

<!--
==========================================================================
📌 SECTION PURPOSE: Last-minute review before interview
==========================================================================

PRINT THIS OUT or memorize it!

Review 10 minutes before your interview:
1. Scan this table
2. Make sure you can explain each answer in 30 seconds
3. Deep breathe, you've got this!
==========================================================================
-->

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
