# Notification System - System Design Document

> A beginner-friendly notification system built with Spring Boot, following Alex Xu's system design approach.

---

## Table of Contents

1. [Step 1: Understand the Problem & Scope](#step-1-understand-the-problem--scope)
2. [Step 2: High-Level Design](#step-2-high-level-design)
3. [Step 3: Design Deep Dive](#step-3-design-deep-dive)
4. [Step 4: Database Schema](#step-4-database-schema)
5. [Step 5: API Design](#step-5-api-design)
6. [Step 6: Back of the Envelope Estimation](#step-6-back-of-the-envelope-estimation)
7. [Step 7: Project Structure](#step-7-project-structure)
8. [Step 8: Implementation Guide](#step-8-implementation-guide)
9. [Step 9: How to Run & Test](#step-9-how-to-run--test)

---

## Step 1: Understand the Problem & Scope

### 1.1 What is a Notification System?

A notification system sends alerts/messages to users through different channels:

```
┌─────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION TYPES                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  📧 EMAIL        → "Your order has been shipped!"               │
│  📱 SMS          → "Your OTP is 123456"                         │
│  🔔 PUSH         → "John liked your photo"                      │
│  💬 IN-APP       → "You have 3 new messages"                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Functional Requirements

**What the system MUST do:**

| Requirement | Description | Example |
|------------|-------------|---------|
| Send notifications | Deliver messages via different channels | Send welcome email to new user |
| Multiple channels | Support Email, SMS, Push, In-App | User gets SMS for OTP, email for receipts |
| User preferences | Let users choose what they want | "Don't send me marketing emails" |
| Rate limiting | Prevent spam | Max 5 SMS per hour per user |
| Retry failed sends | Don't lose notifications | Retry email if SendGrid is down |
| Track status | Know if notification was delivered | "Email sent at 10:30 AM" |

### 1.3 Non-Functional Requirements

**How well the system should perform:**

| Requirement | Target | Why it matters |
|------------|--------|----------------|
| Availability | 99.9% uptime | Notifications are critical |
| Latency | < 500ms API response | Users expect fast response |
| Throughput | 1000 notifications/sec | Handle traffic spikes |
| Reliability | No lost notifications | Every notification matters |

### 1.4 Out of Scope (To Keep It Simple)

We will NOT implement these (but mention them for awareness):
- ❌ Analytics dashboard
- ❌ A/B testing for notifications
- ❌ Complex scheduling (cron expressions)
- ❌ Multi-tenancy
- ❌ Webhooks

---

## Step 2: High-Level Design

### 2.1 Simple Architecture (What We'll Build)

```
                                USER/SERVICE
                                     │
                                     │ HTTP Request
                                     ▼
                        ┌─────────────────────────┐
                        │                         │
                        │   NOTIFICATION SERVICE  │ ◄── Spring Boot App
                        │      (REST API)         │
                        │                         │
                        └───────────┬─────────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
              ▼                     ▼                     ▼
        ┌───────────┐        ┌───────────┐        ┌───────────┐
        │ PostgreSQL│        │   Redis   │        │   Kafka   │
        │ (Database)│        │  (Cache)  │        │  (Queue)  │
        └───────────┘        └───────────┘        └─────┬─────┘
                                                        │
                                                        │ Consume
                                                        ▼
                                            ┌─────────────────────┐
                                            │   WORKER SERVICE    │
                                            │  (Sends via Email,  │
                                            │   SMS, Push, etc.)  │
                                            └─────────────────────┘
                                                        │
                      ┌─────────────────┬───────────────┼───────────────┐
                      ▼                 ▼               ▼               ▼
                ┌──────────┐     ┌──────────┐    ┌──────────┐    ┌──────────┐
                │ SendGrid │     │  Twilio  │    │   FCM    │    │ Database │
                │ (Email)  │     │  (SMS)   │    │  (Push)  │    │ (In-App) │
                └──────────┘     └──────────┘    └──────────┘    └──────────┘
```

### 2.2 Component Responsibilities

| Component | What it does | Technology |
|-----------|--------------|------------|
| **Notification Service** | Receives requests, validates, queues | Spring Boot REST API |
| **PostgreSQL** | Stores notifications, users, templates | Relational Database |
| **Redis** | Rate limiting, caching | In-memory store |
| **Kafka** | Message queue for async processing | Message Broker |
| **Worker Service** | Picks from queue, sends notifications | Spring Boot + Kafka Consumer |
| **External Providers** | Actually delivers the notification | SendGrid, Twilio, FCM |

### 2.3 Why This Architecture?

```
┌─────────────────────────────────────────────────────────────────────┐
│                     WHY WE USE A MESSAGE QUEUE                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  WITHOUT QUEUE (Bad):                                                │
│  ──────────────────                                                  │
│  Client ──► API ──► Send Email ──► Wait... ──► Response (slow!)     │
│                          │                                           │
│                          └── If email fails, request fails!          │
│                                                                      │
│  WITH QUEUE (Good):                                                  │
│  ─────────────────                                                   │
│  Client ──► API ──► Save to Queue ──► Response (fast!)              │
│                          │                                           │
│                          └── Worker picks up and sends later         │
│                               (Retry if failed)                      │
│                                                                      │
│  Benefits:                                                           │
│  ├── Fast API response (don't wait for email to send)                │
│  ├── Retry failed sends automatically                                │
│  ├── Handle traffic spikes (queue absorbs load)                      │
│  └── Scale workers independently                                     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Step 3: Design Deep Dive

### 3.1 How a Notification Gets Sent (Step by Step)

```
┌─────────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION FLOW (Detailed)                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Step 1: Client sends request                                        │
│  ─────────────────────────────                                       │
│  POST /api/v1/notifications                                          │
│  {                                                                   │
│    "userId": "user-123",                                             │
│    "channel": "EMAIL",                                               │
│    "templateId": "welcome-email",                                    │
│    "data": { "userName": "John" }                                    │
│  }                                                                   │
│                                                                      │
│  Step 2: API validates request                                       │
│  ─────────────────────────────                                       │
│  ├── Is userId valid?                                                │
│  ├── Does template exist?                                            │
│  ├── Is channel supported?                                           │
│  └── Is user rate limited?                                           │
│                                                                      │
│  Step 3: Check user preferences                                      │
│  ─────────────────────────────                                       │
│  ├── Has user enabled EMAIL notifications?                           │
│  └── Is user in "quiet hours"?                                       │
│                                                                      │
│  Step 4: Save to database                                            │
│  ─────────────────────────────                                       │
│  INSERT INTO notifications (id, user_id, channel, status, ...)       │
│  status = 'PENDING'                                                  │
│                                                                      │
│  Step 5: Publish to Kafka queue                                      │
│  ─────────────────────────────                                       │
│  Topic: "notifications" → { notificationId: "abc-123" }              │
│                                                                      │
│  Step 6: Return response to client                                   │
│  ─────────────────────────────                                       │
│  HTTP 202 Accepted                                                   │
│  { "id": "abc-123", "status": "PENDING" }                            │
│                                                                      │
│  Step 7: Worker picks up message (async)                             │
│  ─────────────────────────────                                       │
│  Kafka Consumer → reads notification from queue                      │
│                                                                      │
│  Step 8: Worker sends notification                                   │
│  ─────────────────────────────                                       │
│  ├── Fetch notification from DB                                      │
│  ├── Process template (replace {{userName}} with "John")             │
│  ├── Call SendGrid API to send email                                 │
│  └── Update status to 'SENT' or 'FAILED'                             │
│                                                                      │
│  Step 9: Handle failure (if needed)                                  │
│  ─────────────────────────────                                       │
│  If failed → increment retry_count → schedule retry                  │
│  If max retries reached → mark as FAILED                             │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Rate Limiting (Preventing Spam)

```
┌─────────────────────────────────────────────────────────────────────┐
│                      RATE LIMITING EXPLAINED                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Problem: Without limits, we could spam users                        │
│  ────────                                                            │
│  - Bug sends 1000 emails to same user                                │
│  - Attacker floods our system                                        │
│  - We exceed SendGrid/Twilio limits (and pay more!)                  │
│                                                                      │
│  Solution: Token Bucket Algorithm                                    │
│  ────────                                                            │
│                                                                      │
│  Imagine a bucket that holds tokens:                                 │
│                                                                      │
│     ┌─────────────┐                                                  │
│     │ 🪙 🪙 🪙 🪙 │  ← Bucket with 5 tokens (max capacity)           │
│     │ 🪙          │                                                  │
│     └─────────────┘                                                  │
│                                                                      │
│  Rules:                                                              │
│  ├── Each notification request takes 1 token                         │
│  ├── Bucket refills 1 token per minute                               │
│  ├── If bucket is empty → REJECT request                             │
│  └── Bucket never exceeds max capacity                               │
│                                                                      │
│  Example (5 tokens max, 1 per minute refill):                        │
│  ─────────────────────────────────────────────                       │
│  10:00 - User sends email (5→4 tokens) ✓                             │
│  10:00 - User sends email (4→3 tokens) ✓                             │
│  10:00 - User sends email (3→2 tokens) ✓                             │
│  10:00 - User sends email (2→1 tokens) ✓                             │
│  10:00 - User sends email (1→0 tokens) ✓                             │
│  10:00 - User sends email (0 tokens) ✗ RATE LIMITED!                 │
│  10:01 - Refill (+1 token, now 1)                                    │
│  10:01 - User sends email (1→0 tokens) ✓                             │
│                                                                      │
│  We store token count in Redis (fast!):                              │
│  Key: "rate_limit:EMAIL:user-123" → Value: "3" (tokens left)         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.3 Retry Strategy (Don't Lose Notifications)

```
┌─────────────────────────────────────────────────────────────────────┐
│                      RETRY WITH EXPONENTIAL BACKOFF                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Problem: External services can fail temporarily                     │
│  ────────                                                            │
│  - SendGrid is down for 2 minutes                                    │
│  - Network timeout                                                   │
│  - Twilio rate limits us                                             │
│                                                                      │
│  Bad Solution: Retry immediately in a loop                           │
│  ────────────                                                        │
│  └── Hammers the failing service, makes things worse!                │
│                                                                      │
│  Good Solution: Exponential Backoff                                  │
│  ────────────                                                        │
│  Wait longer between each retry:                                     │
│                                                                      │
│     Attempt │ Wait Time │ Total Time                                 │
│     ────────┼───────────┼────────────                                │
│        1    │  0 sec    │   0 sec     (immediate)                    │
│        2    │  1 min    │   1 min                                    │
│        3    │  5 min    │   6 min                                    │
│        4    │  30 min   │  36 min                                    │
│        5    │  2 hours  │  ~2.5 hours                                │
│     ────────┴───────────┴────────────                                │
│        ❌   │  Give up, mark as FAILED                               │
│                                                                      │
│  Why Exponential?                                                    │
│  ├── Gives service time to recover                                   │
│  ├── Doesn't overwhelm with retries                                  │
│  └── Eventually gives up (finite attempts)                           │
│                                                                      │
│  Implementation:                                                     │
│  ───────────────                                                     │
│  next_retry_at = now + (base_delay * 2^attempt) + random_jitter      │
│                                                                      │
│  The random_jitter prevents "thundering herd":                       │
│  └── 1000 failed notifications don't all retry at exact same time   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.4 Template System (Reusable Messages)

```
┌─────────────────────────────────────────────────────────────────────┐
│                      TEMPLATE SYSTEM                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Problem: Hardcoding messages is bad                                 │
│  ────────                                                            │
│  - "Hello John, your order #123 is shipped" (hardcoded)              │
│  - Need to change text? Redeploy the app!                            │
│  - Different languages? Copy-paste nightmare!                        │
│                                                                      │
│  Solution: Templates with Variables                                  │
│  ────────                                                            │
│                                                                      │
│  Template (stored in database):                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Name: "order-shipped"                                        │   │
│  │  Subject: "Your order #{{orderId}} is on its way!"            │   │
│  │  Body: "Hello {{userName}},                                   │   │
│  │         Your order #{{orderId}} has been shipped.             │   │
│  │         Track it here: {{trackingUrl}}"                       │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Request data:                                                       │
│  {                                                                   │
│    "templateId": "order-shipped",                                    │
│    "data": {                                                         │
│      "userName": "John",                                             │
│      "orderId": "12345",                                             │
│      "trackingUrl": "https://track.example.com/12345"                │
│    }                                                                 │
│  }                                                                   │
│                                                                      │
│  Result after processing:                                            │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Subject: "Your order #12345 is on its way!"                  │   │
│  │  Body: "Hello John,                                           │   │
│  │         Your order #12345 has been shipped.                   │   │
│  │         Track it here: https://track.example.com/12345"       │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Benefits:                                                           │
│  ├── Change text without code changes                                │
│  ├── Marketing team can edit templates                               │
│  ├── Easy to add new languages                                       │
│  └── Consistent messaging across the app                             │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Step 4: Database Schema

### 4.1 Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DATABASE TABLES OVERVIEW                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐         ┌──────────────────────┐                  │
│  │    users     │         │ notification_templates│                  │
│  ├──────────────┤         ├──────────────────────┤                  │
│  │ id (PK)      │         │ id (PK)              │                  │
│  │ email        │         │ name                 │                  │
│  │ phone        │         │ channel              │                  │
│  │ device_token │         │ subject_template     │                  │
│  │ created_at   │         │ body_template        │                  │
│  └──────┬───────┘         │ created_at           │                  │
│         │                 └───────────┬──────────┘                  │
│         │ 1:N                         │                              │
│         ▼                             │ 1:N                          │
│  ┌──────────────────┐                 │                              │
│  │ user_preferences │                 │                              │
│  ├──────────────────┤                 │                              │
│  │ id (PK)          │                 │                              │
│  │ user_id (FK)     │                 │                              │
│  │ channel          │                 │                              │
│  │ enabled          │                 │                              │
│  └──────────────────┘                 │                              │
│         │                             │                              │
│         │                             │                              │
│         │         ┌───────────────────┘                              │
│         │         │                                                  │
│         ▼         ▼                                                  │
│  ┌────────────────────────────────────────┐                         │
│  │            notifications               │                         │
│  ├────────────────────────────────────────┤                         │
│  │ id (PK)                                │ ◄── Main table!         │
│  │ user_id (FK)                           │                         │
│  │ template_id (FK) - optional            │                         │
│  │ channel (EMAIL/SMS/PUSH/IN_APP)        │                         │
│  │ priority (HIGH/MEDIUM/LOW)             │                         │
│  │ status (PENDING/SENT/DELIVERED/FAILED) │                         │
│  │ subject                                │                         │
│  │ content                                │                         │
│  │ retry_count                            │                         │
│  │ next_retry_at                          │                         │
│  │ sent_at                                │                         │
│  │ created_at                             │                         │
│  └────────────────────────────────────────┘                         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 SQL Schema (With Comments)

```sql
-- =====================================================
-- NOTIFICATION SYSTEM DATABASE SCHEMA
-- =====================================================

-- Enable UUID generation (PostgreSQL)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- TABLE: users
-- Purpose: Store user information for sending notifications
-- =====================================================
CREATE TABLE users (
    -- Primary key: Unique identifier for each user
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Email address for sending email notifications
    email VARCHAR(255) UNIQUE,
    
    -- Phone number for SMS notifications (E.164 format: +1234567890)
    phone VARCHAR(20),
    
    -- Device token for push notifications (from mobile app)
    device_token VARCHAR(500),
    
    -- Timestamps for tracking
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast email lookups
CREATE INDEX idx_users_email ON users(email);

-- =====================================================
-- TABLE: user_preferences
-- Purpose: Let users control what notifications they receive
-- =====================================================
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Which user this preference belongs to
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- Which channel (EMAIL, SMS, PUSH, IN_APP)
    channel VARCHAR(20) NOT NULL,
    
    -- Is this channel enabled? (true = send, false = don't send)
    enabled BOOLEAN DEFAULT true,
    
    -- Quiet hours: Don't send notifications during this time
    -- Example: quiet_hours_start = 22:00, quiet_hours_end = 08:00
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Each user can only have one preference per channel
    UNIQUE(user_id, channel)
);

-- =====================================================
-- TABLE: notification_templates
-- Purpose: Reusable message templates with placeholders
-- =====================================================
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Unique name to identify template (e.g., "welcome-email")
    name VARCHAR(100) NOT NULL UNIQUE,
    
    -- Which channel this template is for
    channel VARCHAR(20) NOT NULL,
    
    -- Subject line with placeholders (e.g., "Hello {{userName}}")
    subject_template VARCHAR(500),
    
    -- Message body with placeholders
    body_template TEXT NOT NULL,
    
    -- Is this template active? (allows soft disable)
    is_active BOOLEAN DEFAULT true,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- TABLE: notifications (MAIN TABLE)
-- Purpose: Store every notification sent through the system
-- =====================================================
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Who is this notification for?
    user_id UUID NOT NULL REFERENCES users(id),
    
    -- Which template was used? (optional, can send without template)
    template_id UUID REFERENCES notification_templates(id),
    
    -- How to send: EMAIL, SMS, PUSH, or IN_APP
    channel VARCHAR(20) NOT NULL,
    
    -- How important: HIGH (send first), MEDIUM, LOW (can wait)
    priority VARCHAR(10) DEFAULT 'MEDIUM',
    
    -- Current state of the notification
    -- PENDING  → Just created, waiting to be processed
    -- SENT     → Successfully sent to provider (SendGrid/Twilio)
    -- DELIVERED→ Confirmed delivered to user
    -- FAILED   → All retries exhausted, gave up
    status VARCHAR(20) DEFAULT 'PENDING',
    
    -- The actual message content (after template processing)
    subject VARCHAR(500),
    content TEXT NOT NULL,
    
    -- Retry tracking
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_retry_at TIMESTAMP,  -- When to retry if failed
    error_message TEXT,       -- Why did it fail?
    
    -- Timestamps for tracking
    sent_at TIMESTAMP,       -- When was it sent?
    delivered_at TIMESTAMP,  -- When was it delivered?
    read_at TIMESTAMP,       -- When was it read? (for in-app)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_channel ON notifications(channel);

-- Index for finding notifications that need retry
CREATE INDEX idx_notifications_retry ON notifications(next_retry_at) 
    WHERE status = 'PENDING' AND next_retry_at IS NOT NULL;
```

### 4.3 Status Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                 NOTIFICATION STATUS FLOW                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│                        ┌──────────┐                                  │
│                        │ PENDING  │ ◄── Initial state                │
│                        └────┬─────┘                                  │
│                             │                                        │
│                             │ Worker picks up                        │
│                             ▼                                        │
│                        ┌──────────┐                                  │
│                        │ SENDING  │ ◄── Being processed              │
│                        └────┬─────┘                                  │
│                             │                                        │
│              ┌──────────────┼──────────────┐                         │
│              │              │              │                         │
│         Success         Failure      Permanent                       │
│              │              │         Failure                        │
│              ▼              ▼              │                         │
│         ┌────────┐   ┌──────────┐          │                         │
│         │  SENT  │   │ PENDING  │          │                         │
│         └────┬───┘   │(retry++)│          │                         │
│              │       └────┬─────┘          │                         │
│              │            │                │                         │
│              │            │ Max retries    │                         │
│              │            │ reached        │                         │
│              │            │                ▼                         │
│              │            │          ┌──────────┐                    │
│              │            └─────────►│  FAILED  │                    │
│              │                       └──────────┘                    │
│              │                                                       │
│              │ Provider confirms                                     │
│              ▼                                                       │
│         ┌───────────┐                                                │
│         │ DELIVERED │                                                │
│         └─────┬─────┘                                                │
│               │                                                      │
│               │ User opens (in-app only)                             │
│               ▼                                                      │
│           ┌──────┐                                                   │
│           │ READ │                                                   │
│           └──────┘                                                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Step 5: API Design

### 5.1 API Endpoints Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        REST API ENDPOINTS                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  NOTIFICATIONS                                                       │
│  ─────────────                                                       │
│  POST   /api/v1/notifications           → Send a notification        │
│  GET    /api/v1/notifications/{id}      → Get notification status    │
│  GET    /api/v1/notifications/user/{id} → Get user's notifications   │
│  PUT    /api/v1/notifications/{id}/read → Mark as read               │
│                                                                      │
│  TEMPLATES                                                           │
│  ─────────                                                           │
│  POST   /api/v1/templates               → Create template            │
│  GET    /api/v1/templates               → List all templates         │
│  GET    /api/v1/templates/{id}          → Get template details       │
│  PUT    /api/v1/templates/{id}          → Update template            │
│  DELETE /api/v1/templates/{id}          → Delete template            │
│                                                                      │
│  USER PREFERENCES                                                    │
│  ────────────────                                                    │
│  GET    /api/v1/users/{id}/preferences  → Get user preferences       │
│  PUT    /api/v1/users/{id}/preferences  → Update preferences         │
│                                                                      │
│  HEALTH (for monitoring)                                             │
│  ──────                                                              │
│  GET    /actuator/health                → Is the service healthy?    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 API Examples

#### Send Notification
```http
POST /api/v1/notifications
Content-Type: application/json

{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "channel": "EMAIL",
    "priority": "HIGH",
    "templateId": "welcome-email",
    "data": {
        "userName": "John Doe",
        "activationLink": "https://example.com/activate/abc123"
    }
}

Response: 202 Accepted
{
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "status": "PENDING",
    "message": "Notification queued for delivery"
}
```

#### Get Notification Status
```http
GET /api/v1/notifications/660e8400-e29b-41d4-a716-446655440001

Response: 200 OK
{
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "channel": "EMAIL",
    "status": "DELIVERED",
    "subject": "Welcome to Our Platform, John Doe!",
    "sentAt": "2024-01-15T10:30:00Z",
    "deliveredAt": "2024-01-15T10:30:05Z"
}
```

#### Get User's Notifications (Inbox)
```http
GET /api/v1/notifications/user/550e8400-e29b-41d4-a716-446655440000?page=0&size=10

Response: 200 OK
{
    "content": [
        {
            "id": "notification-1",
            "subject": "Welcome!",
            "status": "DELIVERED",
            "createdAt": "2024-01-15T10:30:00Z"
        },
        {
            "id": "notification-2",
            "subject": "Your order shipped",
            "status": "READ",
            "createdAt": "2024-01-14T15:00:00Z"
        }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 25,
    "totalPages": 3
}
```

#### Create Template
```http
POST /api/v1/templates
Content-Type: application/json

{
    "name": "order-confirmation",
    "channel": "EMAIL",
    "subjectTemplate": "Order #{{orderId}} Confirmed!",
    "bodyTemplate": "Hi {{userName}}, your order #{{orderId}} for {{itemCount}} item(s) has been confirmed. Total: {{orderTotal}}"
}

Response: 201 Created
{
    "id": "template-123",
    "name": "order-confirmation",
    "message": "Template created successfully"
}
```

#### Update User Preferences
```http
PUT /api/v1/users/550e8400-e29b-41d4-a716-446655440000/preferences
Content-Type: application/json

{
    "preferences": [
        {
            "channel": "EMAIL",
            "enabled": true
        },
        {
            "channel": "SMS",
            "enabled": false
        },
        {
            "channel": "PUSH",
            "enabled": true,
            "quietHoursStart": "22:00",
            "quietHoursEnd": "08:00"
        }
    ]
}

Response: 200 OK
{
    "message": "Preferences updated successfully"
}
```

### 5.3 HTTP Status Codes

```
┌─────────────────────────────────────────────────────────────────────┐
│                    HTTP STATUS CODES WE USE                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  2XX - Success                                                       │
│  ─────────────                                                       │
│  200 OK         → Request successful (GET, PUT)                      │
│  201 Created    → Resource created (POST template)                   │
│  202 Accepted   → Request accepted, processing async (POST notif)   │
│  204 No Content → Deleted successfully                               │
│                                                                      │
│  4XX - Client Error (Your fault)                                     │
│  ──────────────────────────────                                      │
│  400 Bad Request     → Invalid JSON, missing fields                  │
│  401 Unauthorized    → Missing or invalid API key                    │
│  404 Not Found       → User/template/notification doesn't exist      │
│  429 Too Many Reqs   → Rate limit exceeded                           │
│                                                                      │
│  5XX - Server Error (Our fault)                                      │
│  ──────────────────────────────                                      │
│  500 Internal Error  → Something broke, we're looking into it        │
│  503 Unavailable     → Service is down, try again later              │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Step 6: Back of the Envelope Estimation

### 6.1 Traffic Estimation

```
┌─────────────────────────────────────────────────────────────────────┐
│                    TRAFFIC CALCULATIONS                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Assumptions (for a medium-sized app):                               │
│  ──────────────────────────────────────                              │
│  • 1 million users                                                   │
│  • Each user receives 3 notifications per day on average             │
│  • Peak traffic is 5x the average                                    │
│                                                                      │
│  Daily Volume:                                                       │
│  ─────────────                                                       │
│  Total notifications/day = 1M users × 3 notifications                │
│                          = 3 million notifications/day               │
│                                                                      │
│  Notifications Per Second (Average):                                 │
│  ───────────────────────────────────                                 │
│  QPS = 3,000,000 / 86,400 seconds                                    │
│      = 35 notifications/second                                       │
│                                                                      │
│  Peak QPS:                                                           │
│  ─────────                                                           │
│  Peak QPS = 35 × 5 = 175 notifications/second                        │
│                                                                      │
│  This is very manageable for our simple system!                      │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Storage Estimation

```
┌─────────────────────────────────────────────────────────────────────┐
│                    STORAGE CALCULATIONS                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Size of one notification record:                                    │
│  ─────────────────────────────────                                   │
│  id (UUID)         = 16 bytes                                        │
│  user_id (UUID)    = 16 bytes                                        │
│  channel           = 10 bytes                                        │
│  priority          = 10 bytes                                        │
│  status            = 15 bytes                                        │
│  subject           = 200 bytes (average)                             │
│  content           = 500 bytes (average)                             │
│  timestamps        = 40 bytes                                        │
│  other fields      = ~200 bytes                                      │
│  ──────────────────────────────                                      │
│  Total per record  ≈ 1 KB (1000 bytes)                               │
│                                                                      │
│  Daily Storage:                                                      │
│  ──────────────                                                      │
│  3 million × 1 KB = 3 GB per day                                     │
│                                                                      │
│  Monthly Storage:                                                    │
│  ───────────────                                                     │
│  3 GB × 30 days = 90 GB per month                                    │
│                                                                      │
│  Yearly Storage (if we keep everything):                             │
│  ────────────────────────────────────────                            │
│  90 GB × 12 = 1.08 TB per year                                       │
│                                                                      │
│  Recommendation:                                                     │
│  ───────────────                                                     │
│  • Keep last 90 days in main database                                │
│  • Archive older data to cold storage (S3)                           │
│  • This keeps database size around ~270 GB                           │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.3 Infrastructure Sizing

```
┌─────────────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE SIZING                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  For our load (175 peak QPS), we need:                               │
│                                                                      │
│  Application Servers:                                                │
│  ────────────────────                                                │
│  • 2 instances (for redundancy)                                      │
│  • Each can handle 500+ req/sec easily                               │
│  • Size: 2 vCPU, 4GB RAM each                                        │
│                                                                      │
│  Database (PostgreSQL):                                              │
│  ──────────────────────                                              │
│  • 1 primary instance                                                │
│  • Size: 4 vCPU, 16GB RAM, 500GB SSD                                 │
│  • Optional: 1 read replica                                          │
│                                                                      │
│  Redis (Cache + Rate Limiting):                                      │
│  ──────────────────────────────                                      │
│  • 1 instance                                                        │
│  • Size: 2GB RAM is plenty                                           │
│                                                                      │
│  Kafka (Message Queue):                                              │
│  ──────────────────────                                              │
│  • For development: 3-broker cluster with replication factor 3       │
│  • For production: 3+ Kafka brokers (or managed service)             │
│                                                                      │
│  Estimated Monthly Cost (AWS/GCP):                                   │
│  ─────────────────────────────────                                   │
│  • App servers: $50-100                                              │
│  • Database: $100-200                                                │
│  • Redis: $20-50                                                     │
│  • Total: ~$200-400/month                                            │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Step 7: Project Structure

```
notification-system/
│
├── src/main/java/com/notification/
│   │
│   ├── NotificationApplication.java      # Main entry point
│   │
│   ├── config/                           # Configuration classes
│   │   ├── KafkaConfig.java              # Kafka producer/consumer setup
│   │   ├── RedisConfig.java              # Redis connection setup
│   │   └── OpenApiConfig.java            # Swagger documentation
│   │
│   ├── controller/                       # REST API endpoints
│   │   ├── NotificationController.java   # /api/v1/notifications
│   │   ├── TemplateController.java       # /api/v1/templates
│   │   └── UserPreferenceController.java # /api/v1/users/{id}/preferences
│   │
│   ├── service/                          # Business logic
│   │   ├── NotificationService.java      # Core notification logic
│   │   ├── TemplateService.java          # Template CRUD + processing
│   │   ├── UserPreferenceService.java    # User preference management
│   │   ├── RateLimiterService.java       # Rate limiting with Redis
│   │   │
│   │   └── channel/                      # Different delivery channels
│   │       ├── NotificationChannel.java  # Interface (contract)
│   │       ├── EmailChannel.java         # SendGrid integration
│   │       ├── SmsChannel.java           # Twilio integration
│   │       ├── PushChannel.java          # FCM integration
│   │       └── InAppChannel.java         # Database storage
│   │
│   ├── model/                            # Data structures
│   │   ├── entity/                       # Database tables (JPA)
│   │   │   ├── User.java
│   │   │   ├── UserPreference.java
│   │   │   ├── Notification.java
│   │   │   └── NotificationTemplate.java
│   │   │
│   │   ├── dto/                          # API request/response objects
│   │   │   ├── NotificationRequest.java
│   │   │   ├── NotificationResponse.java
│   │   │   ├── TemplateRequest.java
│   │   │   └── TemplateResponse.java
│   │   │
│   │   └── enums/                        # Constants
│   │       ├── ChannelType.java          # EMAIL, SMS, PUSH, IN_APP
│   │       ├── Priority.java             # HIGH, MEDIUM, LOW
│   │       └── NotificationStatus.java   # PENDING, SENT, DELIVERED, FAILED
│   │
│   ├── repository/                       # Database access
│   │   ├── NotificationRepository.java
│   │   ├── TemplateRepository.java
│   │   ├── UserRepository.java
│   │   └── UserPreferenceRepository.java
│   │
│   ├── messaging/                        # Kafka integration
│   │   ├── NotificationProducer.java     # Sends to Kafka
│   │   └── NotificationConsumer.java     # Receives from Kafka
│   │
│   ├── scheduler/                        # Background jobs
│   │   └── RetryScheduler.java           # Retries failed notifications
│   │
│   └── exception/                        # Error handling
│       ├── GlobalExceptionHandler.java   # Catches all errors
│       ├── NotFoundException.java        # 404 errors
│       └── RateLimitException.java       # 429 errors
│
├── src/main/resources/
│   ├── application.yml                   # Main configuration
│   ├── application-dev.yml               # Development settings
│   └── db/migration/                     # Database migrations
│       └── V1__init_schema.sql           # Initial tables
│
├── src/test/java/com/notification/       # Tests
│   ├── service/
│   │   └── NotificationServiceTest.java
│   └── controller/
│       └── NotificationControllerTest.java
│
├── docker-compose.yml                    # Local development setup
├── pom.xml                               # Maven dependencies
└── README.md                             # How to run the project
```

---

## Step 8: Implementation Guide

### 8.1 Technologies We'll Use

| Technology | Purpose | Why This? |
|------------|---------|-----------|
| **Java 17** | Programming language | Modern, widely used, LTS |
| **Spring Boot 3** | Web framework | Easy to learn, powerful |
| **PostgreSQL** | Database | Reliable, free, feature-rich |
| **Redis** | Caching & rate limiting | Fast, simple |
| **Kafka** | Message queue | Reliable, scalable |
| **Docker** | Containerization | Easy local setup |
| **JUnit 5** | Testing | Standard for Java |

### 8.2 Key Dependencies (pom.xml)

```xml
<dependencies>
    <!-- Web & REST API -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Database (JPA + PostgreSQL) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    
    <!-- Redis (Caching + Rate Limiting) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- Kafka (Message Queue) -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- API Documentation (Swagger) -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.3.0</version>
    </dependency>
    
    <!-- Lombok (Reduces boilerplate code) -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 8.3 Implementation Order

```
┌─────────────────────────────────────────────────────────────────────┐
│                    IMPLEMENTATION ORDER                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Phase 1: Project Setup (Day 1)                                      │
│  ──────────────────────────────                                      │
│  □ Create Spring Boot project                                        │
│  □ Add dependencies to pom.xml                                       │
│  □ Create docker-compose.yml (PostgreSQL, Redis, Kafka)             │
│  □ Configure application.yml                                         │
│  □ Create database migration (V1__init_schema.sql)                   │
│                                                                      │
│  Phase 2: Domain Model (Day 2)                                       │
│  ─────────────────────────────                                       │
│  □ Create enum classes (ChannelType, Priority, Status)               │
│  □ Create entity classes (User, Notification, Template)             │
│  □ Create DTO classes (request/response)                             │
│  □ Create repository interfaces                                      │
│                                                                      │
│  Phase 3: Core Service (Day 3-4)                                     │
│  ───────────────────────────────                                     │
│  □ Implement NotificationService                                     │
│  □ Implement TemplateService                                         │
│  □ Implement RateLimiterService                                      │
│  □ Create NotificationController (REST API)                          │
│  □ Create TemplateController                                         │
│  □ Add exception handling                                            │
│                                                                      │
│  Phase 4: Async Processing (Day 5)                                   │
│  ─────────────────────────────────                                   │
│  □ Implement NotificationProducer (Kafka)                            │
│  □ Implement NotificationConsumer (Kafka)                            │
│  □ Create channel interface                                          │
│  □ Implement EmailChannel (can be mock for now)                      │
│  □ Implement InAppChannel                                            │
│                                                                      │
│  Phase 5: Advanced Features (Day 6-7)                                │
│  ────────────────────────────────────                                │
│  □ Implement retry mechanism                                         │
│  □ Add user preferences                                              │
│  □ Add basic tests                                                   │
│  □ Write README documentation                                        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Step 9: How to Run & Test

### 9.1 Local Development Setup

```bash
# Step 1: Clone the project
git clone <your-repo>
cd notification-system

# Step 2: Start infrastructure (PostgreSQL, Redis, Kafka)
docker-compose up -d

# Step 3: Run the application
./mvnw spring-boot:run

# Step 4: Access the API
# API: http://localhost:8080/api/v1/notifications
# Swagger: http://localhost:8080/swagger-ui.html
```

### 9.2 Docker Compose (Local Infrastructure)

```yaml
# docker-compose.yml
version: '3.8'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: notification_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  # Redis (Cache + Rate Limiting)
  redis:
    image: redis:7
    ports:
      - "6379:6379"

  # Kafka (Message Queue) - 3 Broker Cluster
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka-1:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3

  kafka-2:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9093:9093"
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3

  kafka-3:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9094:9094"
    environment:
      KAFKA_BROKER_ID: 3
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9094
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3

volumes:
  postgres_data:
```

### 9.3 Testing the API

```bash
# Test 1: Create a user (you'd need this endpoint or seed data)

# Test 2: Send a notification
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "channel": "EMAIL",
    "priority": "HIGH",
    "subject": "Welcome!",
    "content": "Hello, welcome to our platform!"
  }'

# Test 3: Check notification status
curl http://localhost:8080/api/v1/notifications/{notification-id}

# Test 4: Get user's notifications
curl http://localhost:8080/api/v1/notifications/user/{user-id}
```

---

## Summary: What You'll Learn

```
┌─────────────────────────────────────────────────────────────────────┐
│                    KEY LEARNINGS FROM THIS PROJECT                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  System Design Concepts:                                             │
│  ───────────────────────                                             │
│  ✓ How to design a scalable notification system                     │
│  ✓ Why we use message queues (async processing)                     │
│  ✓ Rate limiting to prevent abuse                                   │
│  ✓ Retry strategies for reliability                                 │
│  ✓ Back-of-envelope calculations for sizing                         │
│                                                                      │
│  Spring Boot Skills:                                                 │
│  ───────────────────                                                 │
│  ✓ Building REST APIs with Spring MVC                               │
│  ✓ Database operations with Spring Data JPA                         │
│  ✓ Caching with Redis                                               │
│  ✓ Message queues with Kafka                                        │
│  ✓ Scheduled tasks with @Scheduled                                  │
│  ✓ Exception handling with @ControllerAdvice                        │
│                                                                      │
│  Best Practices:                                                     │
│  ───────────────                                                     │
│  ✓ Clean code organization (layers)                                 │
│  ✓ DTOs for API contracts                                           │
│  ✓ Proper error handling                                            │
│  ✓ API documentation with Swagger                                   │
│  ✓ Docker for local development                                     │
│                                                                      │
│  Interview Talking Points:                                           │
│  ─────────────────────────                                           │
│  • "I designed and built a notification system that handles         │
│     multiple channels (email, SMS, push, in-app)"                   │
│  • "I used Kafka for reliable async processing with retry logic"    │
│  • "I implemented rate limiting using Redis token bucket"           │
│  • "The system can scale to handle 1000+ notifications per second"  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

*Document Version: 2.0 - Simplified for Learning*  
*Approach: Alex Xu System Design*  
*Last Updated: January 2026*
