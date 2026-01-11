# Notification System - API Testing Guide (HTTPie)

A comprehensive guide to testing the Notification System API using **HTTPie** - the human-friendly command-line HTTP client. This guide provides detailed explanations of each endpoint, request/response formats, and testing workflows.

---

## Table of Contents

1. [What is HTTPie?](#what-is-httpie)
2. [Prerequisites](#prerequisites)
3. [HTTPie Installation & Setup](#httpie-installation--setup)
4. [HTTPie Syntax Guide](#httpie-syntax-guide)
5. [Test Data Reference](#test-data-reference)
6. [Health Check Endpoints](#health-check-endpoints)
7. [Notification Endpoints](#notification-endpoints)
8. [Template Endpoints](#template-endpoints)
9. [User Endpoints](#user-endpoints)
10. [OpenAPI Documentation](#openapi-documentation)
11. [Testing Workflows](#testing-workflows)
12. [Error Handling](#error-handling)
13. [Troubleshooting](#troubleshooting)
13. [Shell Variables for Testing](#shell-variables-for-testing)

---

## What is HTTPie?

**HTTPie** (pronounced "aitch-tee-tee-pie") is a modern command-line HTTP client designed for testing APIs. It's an alternative to curl with several advantages:

### HTTPie vs curl Comparison

| Feature | HTTPie | curl |
|---------|--------|------|
| **JSON by default** | ✅ Automatic | ❌ Manual `-H "Content-Type: application/json"` |
| **Syntax** | `http POST :8080/api key=value` | `curl -X POST -H "..." -d '{"key":"value"}'` |
| **Output** | Colored, formatted | Raw text |
| **Headers** | `Header:Value` | `-H "Header: Value"` |
| **Learning curve** | Easy | Steep |

### Example Comparison

**HTTPie:**
```bash
http POST :8080/api/v1/notifications userId=abc channel=EMAIL content="Hello"
```

**Equivalent curl:**
```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"userId":"abc","channel":"EMAIL","content":"Hello"}'
```

---

## Prerequisites

### 1. Start the Infrastructure

Before testing, ensure all Docker services are running:

```bash
# Open Docker Desktop first (required on macOS/Windows)
open -a Docker  # macOS only

# Wait for Docker to start, then run:
docker compose up -d

# Verify all containers are running
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

**Expected Running Containers:**

| Container | Image | Port | Purpose | Health Check |
|-----------|-------|------|---------|--------------|
| `notification-postgres` | postgres:15 | 5432 | Primary database | `pg_isready` |
| `notification-redis` | redis:7-alpine | 6379 | Rate limiting cache | `redis-cli ping` |
| `notification-kafka-1` | confluentinc/cp-kafka:7.4.0 | 9092 | Message queue (Broker 1) | Topic creation |
| `notification-kafka-2` | confluentinc/cp-kafka:7.4.0 | 9093 | Message queue (Broker 2) | Topic creation |
| `notification-kafka-3` | confluentinc/cp-kafka:7.4.0 | 9094 | Message queue (Broker 3) | Topic creation |
| `notification-zookeeper` | confluentinc/cp-zookeeper:7.4.0 | 2181 | Kafka coordination | ZK client |
| `notification-kafka-ui` | provectuslabs/kafka-ui:latest | 8090 | Kafka monitoring UI | HTTP |

### 2. Start the Application

```bash
# Navigate to project directory
cd /path/to/notification-system

# Start Spring Boot application
mvn spring-boot:run
```

**Wait for this log message:**
```
Started NotificationSystemApplication in X.XXX seconds
```

**Application URLs:**
| Service | URL |
|---------|-----|
| API Base | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator | http://localhost:8080/actuator |
| Kafka UI | http://localhost:8090 |

---

## HTTPie Installation & Setup

### Installation

**macOS (Homebrew):**
```bash
brew install httpie
```

**Linux (apt):**
```bash
sudo apt install httpie
```

**Linux (pip):**
```bash
pip install httpie
```

**Windows (pip):**
```bash
pip install httpie
```

**Verify Installation:**
```bash
http --version
# Output: 3.x.x
```

### Recommended Configuration

Create a config file for consistent behavior:

```bash
# Create HTTPie config directory
mkdir -p ~/.config/httpie

# Create config file with defaults
cat > ~/.config/httpie/config.json << 'EOF'
{
    "default_options": ["--style=monokai", "--print=hHbB"]
}
EOF
```

**Config Options Explained:**
- `--style=monokai`: Colorful syntax highlighting
- `--print=hHbB`: Show request headers (h), request body (b), response headers (H), response body (B)

---

## HTTPie Syntax Guide

### Basic Syntax

```bash
http [METHOD] URL [REQUEST_ITEMS...]
```

### HTTP Methods

| Method | HTTPie Syntax | Purpose |
|--------|--------------|---------|
| GET | `http GET :8080/path` or `http :8080/path` | Retrieve data |
| POST | `http POST :8080/path` | Create resource |
| PUT | `http PUT :8080/path` | Update resource |
| DELETE | `http DELETE :8080/path` | Delete resource |
| PATCH | `http PATCH :8080/path` | Partial update |

### Request Items

| Syntax | Type | Example |
|--------|------|---------|
| `key=value` | JSON string | `name=John` → `{"name": "John"}` |
| `key:=value` | JSON non-string | `count:=5` → `{"count": 5}` |
| `key:=true` | JSON boolean | `active:=true` → `{"active": true}` |
| `key:='["a","b"]'` | JSON array | `items:='["a","b"]'` → `{"items": ["a","b"]}` |
| `Header:Value` | HTTP Header | `Accept:application/json` |
| `key==value` | Query parameter | `page==0` → `?page=0` |

### URL Shortcuts

```bash
# These are equivalent:
http GET http://localhost:8080/api/v1/health
http GET localhost:8080/api/v1/health
http GET :8080/api/v1/health
http :8080/api/v1/health  # GET is default
```

### Output Control

| Flag | Description |
|------|-------------|
| `--print=h` | Request headers only |
| `--print=b` | Request body only |
| `--print=H` | Response headers only |
| `--print=B` | Response body only |
| `--print=hb` | Request headers + body |
| `--print=HB` | Response headers + body (default) |
| `-v` or `--verbose` | Show everything |
| `-b` or `--body` | Response body only (shortcut) |
| `-h` or `--headers` | Response headers only (shortcut) |

### Useful Flags

| Flag | Description |
|------|-------------|
| `--json` or `-j` | Force JSON (default) |
| `--form` or `-f` | Form data instead of JSON |
| `--pretty=all` | Format and colorize output |
| `--pretty=none` | Raw output (for piping) |
| `--timeout=30` | Set timeout in seconds |
| `--follow` | Follow redirects |
| `--offline` | Build request without sending |

---

## Test Data Reference

### Pre-seeded Users

The database is pre-populated with test users via Flyway migrations:

| Variable | User ID | Email | Phone | Device Token |
|----------|---------|-------|-------|--------------|
| `USER_1` | `550e8400-e29b-41d4-a716-446655440001` | john.doe@example.com | +1234567890 | device_token_user1 |
| `USER_2` | `550e8400-e29b-41d4-a716-446655440002` | jane.smith@example.com | +0987654321 | device_token_user2 |

### Pre-seeded Templates

| Name | Channel | Template ID | Variables |
|------|---------|-------------|-----------|
| `welcome-email` | EMAIL | `660e8400-e29b-41d4-a716-446655440001` | `{{userName}}` |
| `order-confirmation` | EMAIL | `660e8400-e29b-41d4-a716-446655440002` | `{{userName}}`, `{{orderId}}`, `{{orderTotal}}` |
| `otp-sms` | SMS | `660e8400-e29b-41d4-a716-446655440003` | `{{otpCode}}` |
| `order-shipped` | PUSH | `660e8400-e29b-41d4-a716-446655440004` | `{{orderId}}`, `{{trackingUrl}}` |

### Channel-Specific Kafka Topics

Each notification channel routes to its own Kafka topic (Alex Xu's design pattern):

| Channel | Kafka Topic | Partitions | Consumer Group | Use Case |
|---------|-------------|------------|----------------|----------|
| `EMAIL` | `notifications.email` | 3 | notification-consumer-group-email | Email notifications |
| `SMS` | `notifications.sms` | 2 | notification-consumer-group-sms | Text messages |
| `PUSH` | `notifications.push` | 4 | notification-consumer-group-push | Mobile push |
| `IN_APP` | `notifications.in-app` | 3 | notification-consumer-group-inapp | In-app alerts |
| - | `notifications.dlq` | 1 | - | Dead Letter Queue |

**Why Separate Topics?**
- Independent scaling per channel
- Email delays don't affect push notifications
- Different consumer counts based on volume
- Isolated failure domains

### Priority Levels

| Priority | Value | Processing Order | Use Case |
|----------|-------|------------------|----------|
| `CRITICAL` | 0 | First | OTP, security alerts |
| `HIGH` | 1 | Second | Account alerts, important updates |
| `MEDIUM` | 2 | Third (default) | Order updates, general notifications |
| `LOW` | 3 | Last | Marketing, promotional content |

### Notification Status Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION LIFECYCLE                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌──────────┐    ┌──────────┐    ┌───────────┐    ┌──────────┐│
│   │ PENDING  │───▶│   SENT   │───▶│ DELIVERED │───▶│   READ   ││
│   └──────────┘    └──────────┘    └───────────┘    └──────────┘│
│        │                                                        │
│        ▼                                                        │
│   ┌──────────┐                                                  │
│   │  FAILED  │──(retry if retryCount < 3)──▶ PENDING           │
│   └──────────┘                                                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

| Status | Description | Duration |
|--------|-------------|----------|
| `PENDING` | Queued in Kafka, waiting for consumer | < 1 second typically |
| `SENT` | Delivered to channel handler (email server, SMS gateway) | Immediate |
| `DELIVERED` | Confirmed receipt by end system | Depends on channel |
| `READ` | User acknowledged/opened notification | User-dependent |
| `FAILED` | Delivery failed, may retry | Up to 3 retries |

---

## Health Check Endpoints

Health endpoints verify the API and its dependencies are operational.

---

### 1. Basic Health Check

**Purpose:** Quick verification that the API is responding.

**Request:**
```bash
http :8080/api/v1/health
```

**What Happens:**
1. Request hits `HealthController.healthCheck()`
2. Returns simple "OK" without checking dependencies
3. Fast response (< 10ms)

**Expected Response (200 OK):**
```json
{
    "success": true,
    "message": "Service is healthy",
    "data": "OK",
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | `true` if request succeeded |
| `message` | string | Human-readable status message |
| `data` | string | Health status ("OK") |
| `timestamp` | ISO 8601 | Server timestamp with timezone |

**When to Use:**
- Load balancer health probes
- Quick "is it up?" checks
- Kubernetes liveness probes

---

### 2. Actuator Health Check (Detailed)

**Purpose:** Comprehensive health check of all dependencies.

**Request:**
```bash
http :8080/actuator/health
```

**What Happens:**
1. Spring Boot Actuator checks each component
2. PostgreSQL: Executes validation query
3. Redis: Sends PING command
4. Kafka: Verifies broker connectivity
5. Aggregates all statuses

**Expected Response (200 OK):**
```json
{
    "status": "UP",
    "components": {
        "db": {
            "status": "UP",
            "details": {
                "database": "PostgreSQL",
                "validationQuery": "isValid()"
            }
        },
        "diskSpace": {
            "status": "UP",
            "details": {
                "total": 499963174912,
                "free": 123456789012,
                "threshold": 10485760
            }
        },
        "kafka": {
            "status": "UP"
        },
        "ping": {
            "status": "UP"
        },
        "redis": {
            "status": "UP"
        }
    }
}
```

**Status Values:**

| Status | Meaning | HTTP Code |
|--------|---------|-----------|
| `UP` | Component is healthy | 200 |
| `DOWN` | Component is unhealthy | 503 |
| `OUT_OF_SERVICE` | Component is offline intentionally | 503 |
| `UNKNOWN` | Status cannot be determined | 200 |

**When to Use:**
- Kubernetes readiness probes
- Monitoring dashboards
- Debugging connectivity issues

**Troubleshooting Failed Components:**
```bash
# If db is DOWN:
docker logs notification-postgres

# If redis is DOWN:
docker exec notification-redis redis-cli ping

# If kafka is DOWN:
docker logs notification-kafka
```

---

### 3. Application Info

**Purpose:** Retrieve application metadata and build information.

**Request:**
```bash
http :8080/actuator/info
```

**Expected Response (200 OK):**
```json
{
    "app": {
        "name": "Notification System",
        "description": "Multi-channel notification service",
        "version": "1.0.0"
    }
}
```

**When to Use:**
- Verify deployed version
- CI/CD pipeline validation
- Documentation purposes

---

## Notification Endpoints

Notification endpoints handle sending, retrieving, and managing notifications across all channels.

---

### 1. Send Email Notification (Template-Based)

**Purpose:** Send an email using a pre-defined template with variable substitution.

**Endpoint Details:**

| Property | Value |
|----------|-------|
| **Method** | POST |
| **URL** | `/api/v1/notifications` |
| **Content-Type** | application/json |
| **Kafka Topic** | `notifications.email` |
| **Partitions** | 3 |

**Request:**
```bash
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=EMAIL \
  templateName=welcome-email \
  templateVariables:='{"userName": "John Doe"}' \
  priority=HIGH
```

**Request Body Breakdown:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `userId` | UUID | ✅ Yes | Must exist in `users` table | Target user's ID |
| `channel` | Enum | ✅ Yes | EMAIL, SMS, PUSH, IN_APP | Delivery channel |
| `templateName` | String | ⚠️ Conditional | Must exist and be active | Template to use |
| `templateVariables` | Object | ❌ No | Keys must match template variables | Variable values |
| `priority` | Enum | ❌ No | LOW, MEDIUM, HIGH, CRITICAL | Default: MEDIUM |

**What Happens Internally:**

```
1. Controller receives request
   └─▶ NotificationController.sendNotification()

2. Service layer processing
   └─▶ NotificationService.sendNotification()
       ├─▶ Validate user exists (UserRepository.findById)
       ├─▶ Check rate limit (RateLimiterService - Token Bucket)
       ├─▶ Load template (NotificationTemplateRepository.findByNameAndActive)
       ├─▶ Render template (replace {{variables}})
       └─▶ Create Notification entity (status: PENDING)

3. Kafka publishing
   └─▶ KafkaTemplate.send("notifications.email", notificationId)

4. Async consumer processing
   └─▶ NotificationConsumer.processEmailNotification()
       ├─▶ Load notification from DB
       ├─▶ Dispatch to EmailChannelHandler
       ├─▶ Update status to SENT
       └─▶ Save sentAt timestamp
```

**Expected Response (200 OK):**
```json
{
    "success": true,
    "message": "Notification queued successfully",
    "data": {
        "id": "c57aaec7-80a4-4948-84b8-6d9582737410",
        "userId": "550e8400-e29b-41d4-a716-446655440001",
        "channel": "EMAIL",
        "priority": "HIGH",
        "subject": "Welcome to Our Platform, John Doe!",
        "content": "Hi John Doe,\n\nThank you for joining our platform!...",
        "status": "PENDING",
        "retryCount": 0,
        "createdAt": "2026-01-10T10:30:00.000000+05:30"
    },
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique notification identifier for tracking |
| `userId` | UUID | Target user (copied from request) |
| `channel` | String | Delivery channel used |
| `priority` | String | Priority level |
| `subject` | String | Rendered subject (template variables replaced) |
| `content` | String | Rendered body (template variables replaced) |
| `status` | String | Initial status is always PENDING |
| `retryCount` | Integer | Starts at 0, increments on failure |
| `createdAt` | ISO 8601 | Creation timestamp |

**Verify in Kafka UI:**
1. Open http://localhost:8090
2. Navigate to Topics → `notifications.email`
3. View Messages → You'll see the notification ID

---

### 2. Send Email Notification (Custom Content)

**Purpose:** Send an email with custom subject and body without using a template.

**Request:**
```bash
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=EMAIL \
  subject="Custom Email Subject" \
  content="This is a custom email without a template. You can include any content here." \
  priority=MEDIUM
```

**Request Body Breakdown:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | UUID | ✅ Yes | Target user's ID |
| `channel` | Enum | ✅ Yes | Must be EMAIL |
| `subject` | String | ✅ Yes* | Email subject line |
| `content` | String | ✅ Yes* | Email body text |
| `priority` | Enum | ❌ No | Default: MEDIUM |

*Either `templateName` OR (`subject` + `content`) must be provided.

**When to Use:**
- One-off notifications
- Dynamic content that doesn't fit templates
- Testing and debugging

---

### 3. Send SMS Notification (OTP Example)

**Purpose:** Send a time-sensitive SMS, typically for OTP verification.

**Endpoint Details:**

| Property | Value |
|----------|-------|
| **Kafka Topic** | `notifications.sms` |
| **Partitions** | 2 |
| **Typical Latency** | < 500ms |

**Request:**
```bash
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=SMS \
  templateName=otp-sms \
  templateVariables:='{"otpCode": "847293"}' \
  priority=CRITICAL
```

**Why CRITICAL Priority?**
- OTP codes typically expire in 5-10 minutes
- User is actively waiting for the code
- Bypasses normal queue ordering
- Processed before LOW/MEDIUM/HIGH messages

**SMS Template Rendering:**
```
Template: "Your OTP code is {{otpCode}}. Valid for 5 minutes."
Params:   {"otpCode": "847293"}
Result:   "Your OTP code is 847293. Valid for 5 minutes."
```

**Expected Response (200 OK):**
```json
{
    "success": true,
    "message": "Notification queued successfully",
    "data": {
        "id": "abc123...",
        "userId": "550e8400-e29b-41d4-a716-446655440001",
        "channel": "SMS",
        "priority": "CRITICAL",
        "subject": null,
        "content": "Your OTP code is 847293. Valid for 5 minutes.",
        "status": "PENDING",
        "retryCount": 0,
        "createdAt": "2026-01-10T10:30:00.000000+05:30"
    },
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

**Note:** SMS notifications don't have a subject field (always null).

---

### 4. Send Push Notification

**Purpose:** Send a push notification to user's mobile device.

**Endpoint Details:**

| Property | Value |
|----------|-------|
| **Kafka Topic** | `notifications.push` |
| **Partitions** | 4 (highest due to volume) |

**Request:**
```bash
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440002 \
  channel=PUSH \
  templateName=order-shipped \
  templateVariables:='{"orderId": "ORD-12345", "trackingUrl": "https://track.example.com/ORD-12345"}' \
  priority=HIGH
```

**Why 4 Partitions for Push?**
- Push notifications typically have the highest volume
- More partitions = more parallel consumers
- Each partition can process independently
- Scales to millions of notifications per day

**Push Notification Structure:**
```json
{
    "title": "Your order has shipped!",      // from subjectTemplate
    "body": "Order ORD-12345 is on its way", // from bodyTemplate
    "data": {
        "orderId": "ORD-12345",
        "trackingUrl": "https://track.example.com/ORD-12345"
    }
}
```

---

### 5. Send In-App Notification

**Purpose:** Send a notification to user's in-app notification center.

**Endpoint Details:**

| Property | Value |
|----------|-------|
| **Kafka Topic** | `notifications.in-app` |
| **Partitions** | 3 |

**Request:**
```bash
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=IN_APP \
  subject="New Feature Available!" \
  content="Check out our new dashboard feature with enhanced analytics." \
  priority=LOW
```

**In-App Notification Characteristics:**
- Stored in database for persistent retrieval
- User sees them on next app visit
- Typically displayed as badge/bell icon
- LOW priority appropriate (not time-sensitive)
- Can be marked as READ by user

**Expected Response (200 OK):**
```json
{
    "success": true,
    "message": "Notification queued successfully",
    "data": {
        "id": "def456...",
        "channel": "IN_APP",
        "priority": "LOW",
        "subject": "New Feature Available!",
        "content": "Check out our new dashboard feature with enhanced analytics.",
        "status": "PENDING"
    }
}
```

---

### 6. Send Bulk Notifications

**Purpose:** Send the same notification to multiple users simultaneously.

**Request:**
```bash
http POST :8080/api/v1/notifications/bulk \
  userIds:='["550e8400-e29b-41d4-a716-446655440001", "550e8400-e29b-41d4-a716-446655440002"]' \
  channel=EMAIL \
  templateName=order-confirmation \
  templateVariables:='{"orderId": "ORD-99999", "orderTotal": "$149.99", "userName": "Valued Customer"}' \
  priority=HIGH
```

**Request Body Breakdown:**

| Field | Type | Required | Max Size | Description |
|-------|------|----------|----------|-------------|
| `userIds` | UUID[] | ✅ Yes | 1000 | Array of target user IDs |
| `channel` | Enum | ✅ Yes | - | Single channel for all |
| `templateName` | String | ⚠️ Conditional | - | Template to use |
| `templateVariables` | Object | ❌ No | - | Same params for all users |
| `priority` | Enum | ❌ No | - | Default: MEDIUM |

**What Happens Internally:**
```
For each userId in userIds:
  ├─▶ Validate user exists
  ├─▶ Check rate limit
  ├─▶ Create individual notification
  └─▶ Publish to Kafka topic

Returns aggregate results
```

**Expected Response (200 OK):**
```json
{
    "success": true,
    "message": "Bulk notifications queued",
    "data": {
        "totalRequested": 2,
        "successCount": 2,
        "failedCount": 0,
        "notificationIds": [
            "uuid-1-for-user1",
            "uuid-2-for-user2"
        ],
        "failedUserIds": []
    },
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `totalRequested` | Integer | Number of users in request |
| `successCount` | Integer | Successfully queued |
| `failedCount` | Integer | Failed to queue |
| `notificationIds` | UUID[] | IDs of created notifications |
| `failedUserIds` | UUID[] | Users that failed (rate limit, not found) |

**Use Cases:**
- Marketing campaigns
- System-wide announcements
- Promotional offers
- Service disruption notices

---

### 7. Get Notification by ID

**Purpose:** Retrieve details and current status of a specific notification.

**Request:**
```bash
http :8080/api/v1/notifications/c57aaec7-80a4-4948-84b8-6d9582737410
```

**Expected Response (200 OK):**
```json
{
    "success": true,
    "message": "Notification retrieved",
    "data": {
        "id": "c57aaec7-80a4-4948-84b8-6d9582737410",
        "userId": "550e8400-e29b-41d4-a716-446655440001",
        "channel": "EMAIL",
        "priority": "HIGH",
        "subject": "Welcome to Our Platform, John Doe!",
        "content": "Hi John Doe,\n\nThank you for joining...",
        "status": "SENT",
        "retryCount": 0,
        "createdAt": "2026-01-10T10:30:00.000000+05:30",
        "sentAt": "2026-01-10T10:30:01.000000+05:30",
        "deliveredAt": null,
        "readAt": null,
        "errorMessage": null
    },
    "timestamp": "2026-01-10T10:30:05.000000+05:30"
}
```

**Additional Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `sentAt` | ISO 8601 | When notification was sent to channel |
| `deliveredAt` | ISO 8601 | When delivery was confirmed |
| `readAt` | ISO 8601 | When user read the notification |
| `errorMessage` | String | Error details if FAILED |

**Error Response (404 Not Found):**
```json
{
    "success": false,
    "message": "Notification not found with id: invalid-uuid",
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

---

### 8. Get User Notifications (Paginated)

**Purpose:** Retrieve all notifications for a specific user with pagination.

**Request:**
```bash
http :8080/api/v1/notifications/user/550e8400-e29b-41d4-a716-446655440001 \
  page==0 \
  size==10
```

**Query Parameters:**

| Parameter | Type | Default | Max | Description |
|-----------|------|---------|-----|-------------|
| `page` | Integer | 0 | - | Page number (0-indexed) |
| `size` | Integer | 20 | 100 | Items per page |
| `status` | Enum | - | - | Optional filter |

**Expected Response (200 OK):**
```json
{
    "success": true,
    "message": "Notifications retrieved",
    "data": {
        "content": [
            {
                "id": "uuid-1",
                "channel": "EMAIL",
                "priority": "HIGH",
                "subject": "Welcome!",
                "status": "SENT",
                "createdAt": "2026-01-10T10:30:00.000000+05:30"
            },
            {
                "id": "uuid-2",
                "channel": "SMS",
                "priority": "CRITICAL",
                "subject": null,
                "status": "DELIVERED",
                "createdAt": "2026-01-10T10:25:00.000000+05:30"
            }
        ],
        "pageable": {
            "pageNumber": 0,
            "pageSize": 10,
            "sort": {
                "sorted": true,
                "orderBy": "createdAt DESC"
            }
        },
        "totalElements": 25,
        "totalPages": 3,
        "first": true,
        "last": false
    },
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

**Pagination Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `content` | Array | Notifications for current page |
| `pageNumber` | Integer | Current page (0-indexed) |
| `pageSize` | Integer | Items per page |
| `totalElements` | Integer | Total notifications for user |
| `totalPages` | Integer | Total pages available |
| `first` | Boolean | Is this the first page? |
| `last` | Boolean | Is this the last page? |

---

### 9. Get User Notifications (Filtered by Status)

**Purpose:** Get only notifications with a specific status.

**Request:**
```bash
# Get PENDING notifications
http :8080/api/v1/notifications/user/550e8400-e29b-41d4-a716-446655440001 \
  page==0 \
  size==10 \
  status==PENDING

# Get FAILED notifications (for troubleshooting)
http :8080/api/v1/notifications/user/550e8400-e29b-41d4-a716-446655440001 \
  status==FAILED
```

**Available Status Filters:**

| Status | When to Use |
|--------|-------------|
| `PENDING` | Check queued but unprocessed |
| `SENT` | View successfully sent |
| `DELIVERED` | Confirmed deliveries |
| `FAILED` | Troubleshoot issues |
| `READ` | Track engagement |

---

## Template Endpoints

Templates define reusable message formats with `{{variable}}` placeholders.

---

### 1. Get All Templates

**Purpose:** List all notification templates (active and inactive).

**Request:**
```bash
http :8080/api/v1/templates
```

**Expected Response (200 OK):**
```json
{
    "success": true,
    "message": "Templates retrieved",
    "data": [
        {
            "id": "660e8400-e29b-41d4-a716-446655440001",
            "name": "welcome-email",
            "channel": "EMAIL",
            "subjectTemplate": "Welcome to Our Platform, {{userName}}!",
            "bodyTemplate": "Hi {{userName}},\n\nThank you for joining our platform!...",
            "active": true,
            "createdAt": "2026-01-01T00:00:00.000000+05:30",
            "updatedAt": "2026-01-01T00:00:00.000000+05:30"
        },
        {
            "id": "660e8400-e29b-41d4-a716-446655440003",
            "name": "otp-sms",
            "channel": "SMS",
            "subjectTemplate": null,
            "bodyTemplate": "Your OTP code is {{otpCode}}. Valid for 5 minutes.",
            "active": true,
            "createdAt": "2026-01-01T00:00:00.000000+05:30",
            "updatedAt": "2026-01-01T00:00:00.000000+05:30"
        }
    ],
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

**Template Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique template identifier |
| `name` | String | Unique name used in API calls |
| `channel` | Enum | EMAIL, SMS, PUSH, IN_APP |
| `subjectTemplate` | String | Subject with placeholders (null for SMS) |
| `bodyTemplate` | String | Body with placeholders |
| `active` | Boolean | Can be used for new notifications |

---

### 2. Get Template by ID

**Request:**
```bash
http :8080/api/v1/templates/660e8400-e29b-41d4-a716-446655440001
```

---

### 3. Get Template by Name

**Request:**
```bash
http :8080/api/v1/templates/name/welcome-email
```

---

### 4. Create Email Template

**Purpose:** Create a new email notification template.

**Request:**
```bash
http POST :8080/api/v1/templates \
  name=password-reset \
  channel=EMAIL \
  subjectTemplate="Password Reset Request - {{appName}}" \
  bodyTemplate="Hi {{userName}},\n\nWe received a request to reset your password.\n\nClick the link below to reset it:\n{{resetLink}}\n\nThis link will expire in {{expiryTime}}.\n\nIf you didn't request this, please ignore this email.\n\nBest regards,\nThe {{appName}} Team" \
  active:=true
```

**Request Body Breakdown:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `name` | String | ✅ Yes | Unique, no spaces | Template identifier |
| `channel` | Enum | ✅ Yes | EMAIL, SMS, PUSH, IN_APP | Target channel |
| `subjectTemplate` | String | ❌ No* | - | Subject with `{{vars}}` |
| `bodyTemplate` | String | ✅ Yes | - | Body with `{{vars}}` |
| `active` | Boolean | ❌ No | - | Default: true |

*Subject is required for EMAIL and recommended for PUSH/IN_APP.

**Template Variable Syntax:**
- Use `{{variableName}}` for placeholders
- Variable names are case-sensitive
- Variables must be provided in `templateVariables` when sending
- Missing variables will appear as literal `{{variableName}}`

**Expected Response (201 Created):**
```json
{
    "success": true,
    "message": "Template created",
    "data": {
        "id": "generated-uuid-here",
        "name": "password-reset",
        "channel": "EMAIL",
        "subjectTemplate": "Password Reset Request - {{appName}}",
        "bodyTemplate": "Hi {{userName}},...",
        "active": true,
        "createdAt": "2026-01-10T10:30:00.000000+05:30"
    },
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

---

### 5. Create SMS Template

**Request:**
```bash
http POST :8080/api/v1/templates \
  name=delivery-eta-sms \
  channel=SMS \
  bodyTemplate="Your order {{orderId}} will arrive today between {{timeSlot}}. Track: {{trackingUrl}}" \
  active:=true
```

**SMS Template Notes:**
- No `subjectTemplate` (SMS doesn't support subjects)
- Keep under 160 characters when possible
- Consider character limits for variables

---

### 6. Create Push Template

**Request:**
```bash
http POST :8080/api/v1/templates \
  name=flash-sale-push \
  channel=PUSH \
  subjectTemplate="🔥 Flash Sale Alert!" \
  bodyTemplate="{{discount}}% OFF on {{category}}! Ends in {{hours}} hours. Shop now!" \
  active:=true
```

**Push Template Notes:**
- Emojis supported in subject
- Keep content concise (notification banner limit)
- Subject becomes push notification title

---

### 7. Create In-App Template

**Request:**
```bash
http POST :8080/api/v1/templates \
  name=account-activity-alert \
  channel=IN_APP \
  subjectTemplate="Security Alert" \
  bodyTemplate="New login detected from {{deviceType}} in {{location}} at {{loginTime}}. If this wasn't you, please secure your account." \
  active:=true
```

---

### 8. Update Template

**Purpose:** Modify an existing template.

**Request:**
```bash
http PUT :8080/api/v1/templates/660e8400-e29b-41d4-a716-446655440001 \
  name=welcome-email \
  channel=EMAIL \
  subjectTemplate="Welcome to Our Platform, {{userName}}! 🎉" \
  bodyTemplate="Hi {{userName}},\n\nWelcome aboard! We're thrilled to have you.\n\nHere's what you can do next:\n1. Complete your profile\n2. Explore features\n3. Connect with others\n\nNeed help? Contact support.\n\nBest regards,\nThe Team" \
  active:=true
```

**Note:** All fields are required for PUT (full update). For partial updates, use the same values for unchanged fields.

---

### 9. Delete Template

**Purpose:** Soft-delete a template (sets `active` to false).

**Request:**
```bash
http DELETE :8080/api/v1/templates/660e8400-e29b-41d4-a716-446655440004
```

**Expected Response (200 OK):**
```json
{
    "success": true,
    "message": "Template deleted",
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

**Note:** This is a soft delete:
- Template remains in database
- `active` set to `false`
- Cannot be used for new notifications
- Historical notifications still reference it

---

## User Endpoints

The User endpoints provide cached user lookups for testing Redis caching functionality. These endpoints demonstrate the caching implementation following Alex Xu's system design principles.

### Find User by Email

**Endpoint:** `GET /api/v1/users/email/{email}`

**Purpose:** Retrieve user information by email address with Redis caching.

**Caching:** First request hits database and caches result. Subsequent requests serve from Redis cache without database queries.

```bash
# Test with existing user
http :8080/api/v1/users/email/john@example.com

# Test with non-existent user (will throw exception, not cached)
http :8080/api/v1/users/email/nonexistent@example.com
```

**Response (Success):**
```json
{
  "success": true,
  "message": "User found",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "email": "john@example.com",
    "phone": "+1234567890",
    "deviceToken": "device_token_123",
    "createdAt": "2024-01-15T10:00:00Z",
    "updatedAt": "2024-01-15T10:00:00Z"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Find User by Phone

**Endpoint:** `GET /api/v1/users/phone/{phone}`

**Purpose:** Retrieve user information by phone number with Redis caching.

**Caching:** First request hits database and caches result. Subsequent requests serve from Redis cache.

```bash
# Test with existing user
http :8080/api/v1/users/phone/+1234567890

# Test with non-existent user
http :8080/api/v1/users/phone/+9999999999
```

### Get Push-Eligible Users

**Endpoint:** `GET /api/v1/users/push-eligible`

**Purpose:** Retrieve all users with device tokens for push notifications.

**Caching:** Caches the list of users who can receive push notifications to avoid repeated database queries.

```bash
http :8080/api/v1/users/push-eligible
```

**Response:**
```json
{
  "success": true,
  "message": "Push-eligible users retrieved",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "email": "john@example.com",
      "phone": "+1234567890",
      "deviceToken": "device_token_123",
      "createdAt": "2024-01-15T10:00:00Z",
      "updatedAt": "2024-01-15T10:00:00Z"
    }
  ],
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Cache Testing Workflow:**

1. **Clear Redis cache:**
   ```bash
   docker exec notification-redis redis-cli FLUSHALL
   ```

2. **First request (cache miss - hits database):**
   ```bash
   http :8080/api/v1/users/email/john@example.com
   ```

3. **Check Redis cache:**
   ```bash
   docker exec notification-redis redis-cli keys "*"
   # Should show: users::email:john@example.com
   ```

4. **Second request (cache hit - serves from Redis):**
   ```bash
   http :8080/api/v1/users/email/john@example.com
   ```

5. **Verify no additional database queries** by checking application logs.

---

## OpenAPI Documentation

### Get OpenAPI Spec (JSON)

```bash
http :8080/v3/api-docs
```

### Get OpenAPI Spec (YAML)

```bash
http :8080/v3/api-docs.yaml Accept:application/x-yaml
```

### Swagger UI

Open in browser: **http://localhost:8080/swagger-ui.html**

---

## Testing Workflows

### Workflow 1: Complete Email Notification Flow

Test the entire flow from health check to delivery:

```bash
# Step 1: Verify API health
http :8080/api/v1/health

# Step 2: Check dependencies
http :8080/actuator/health

# Step 3: List available templates
http :8080/api/v1/templates

# Step 4: Send notification
RESPONSE=$(http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=EMAIL \
  templateName=welcome-email \
  templateVariables:='{"userName": "Test User"}' \
  priority=HIGH --print=b)

echo "$RESPONSE"

# Step 5: Extract notification ID and check status
NOTIFICATION_ID=$(echo "$RESPONSE" | jq -r '.data.id')
echo "Notification ID: $NOTIFICATION_ID"

# Step 6: Wait and check status
sleep 2
http :8080/api/v1/notifications/$NOTIFICATION_ID
```

### Workflow 2: Create and Use Custom Template

```bash
# Step 1: Create a new template
http POST :8080/api/v1/templates \
  name=payment-success \
  channel=EMAIL \
  subjectTemplate="Payment Received - {{amount}}" \
  bodyTemplate="Hi {{userName}},\n\nWe received your payment of {{amount}}.\n\nTransaction ID: {{transactionId}}\nDate: {{paymentDate}}\n\nThank you!" \
  active:=true

# Step 2: Verify creation
http :8080/api/v1/templates/name/payment-success

# Step 3: Use the new template
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=EMAIL \
  templateName=payment-success \
  templateVariables:='{"userName": "John Doe", "amount": "$99.99", "transactionId": "TXN-123456", "paymentDate": "January 10, 2026"}' \
  priority=HIGH
```

### Workflow 3: Test All Channels

```bash
echo "=== Testing EMAIL ===" 
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=EMAIL \
  content="Test email notification" \
  priority=HIGH --print=b | jq '.data.id'

echo "=== Testing SMS ==="
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=SMS \
  content="Test SMS notification" \
  priority=HIGH --print=b | jq '.data.id'

echo "=== Testing PUSH ==="
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=PUSH \
  subject="Test Push" \
  content="Test push notification" \
  priority=HIGH --print=b | jq '.data.id'

echo "=== Testing IN_APP ==="
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=IN_APP \
  subject="Test In-App" \
  content="Test in-app notification" \
  priority=HIGH --print=b | jq '.data.id'

echo "=== Check Kafka UI at http://localhost:8090 ==="
```

### Workflow 4: Test Rate Limiting

```bash
# Rate limit is 10 per minute per user per channel
for i in {1..12}; do
  echo "Request $i:"
  http POST :8080/api/v1/notifications \
    userId=550e8400-e29b-41d4-a716-446655440001 \
    channel=EMAIL \
    subject="Rate Limit Test $i" \
    content="Testing rate limiting" \
    priority=LOW --print=b | jq '{success: .success, message: .message}'
  sleep 1
done
```

**Expected:** First 10 succeed, requests 11-12 fail with rate limit error.

### Workflow 5: Test Bulk Notifications

```bash
# Send to both test users
http POST :8080/api/v1/notifications/bulk \
  userIds:='["550e8400-e29b-41d4-a716-446655440001", "550e8400-e29b-41d4-a716-446655440002"]' \
  channel=EMAIL \
  templateName=order-confirmation \
  templateVariables:='{"orderId": "BULK-001", "orderTotal": "$299.99", "userName": "Valued Customer"}' \
  priority=HIGH

# Verify both users received
http :8080/api/v1/notifications/user/550e8400-e29b-41d4-a716-446655440001 size==1
http :8080/api/v1/notifications/user/550e8400-e29b-41d4-a716-446655440002 size==1
```

---

## Error Handling

### Common Error Responses

#### 1. Missing Required Fields (400 Bad Request)

**Trigger:** POST without content or templateName

```bash
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=EMAIL
```

**Response:**
```json
{
    "success": false,
    "message": "Either templateName or content must be provided",
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

#### 2. User Not Found (404 Not Found)

**Trigger:** Invalid userId

```bash
http POST :8080/api/v1/notifications \
  userId=invalid-user-id \
  channel=EMAIL \
  content="Test"
```

**Response:**
```json
{
    "success": false,
    "message": "User not found with id: invalid-user-id",
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

#### 3. Template Not Found (404 Not Found)

```bash
http POST :8080/api/v1/notifications \
  userId=550e8400-e29b-41d4-a716-446655440001 \
  channel=EMAIL \
  templateName=non-existent-template
```

**Response:**
```json
{
    "success": false,
    "message": "Template not found: non-existent-template",
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

#### 4. Rate Limit Exceeded (429 Too Many Requests)

**Trigger:** > 10 requests per minute per user per channel

**Response:**
```json
{
    "success": false,
    "message": "Rate limit exceeded. Please try again later.",
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

**Recovery:**
- Wait 60 seconds for window to reset
- Use different userId
- Use different channel

#### 5. Notification Not Found (404 Not Found)

```bash
http :8080/api/v1/notifications/00000000-0000-0000-0000-000000000000
```

**Response:**
```json
{
    "success": false,
    "message": "Notification not found with id: 00000000-0000-0000-0000-000000000000",
    "timestamp": "2026-01-10T10:30:00.000000+05:30"
}
```

---

## Troubleshooting

### Issue: Connection Refused

**Symptom:**
```
http: error: ConnectionError: HTTPConnectionPool: Max retries exceeded
```

**Solutions:**
1. Verify application is running: `ps aux | grep spring-boot`
2. Start application: `mvn spring-boot:run`
3. Check port availability: `lsof -i :8080`

### Issue: 503 Service Unavailable

**Symptom:** Actuator health returns DOWN

**Diagnosis:**
```bash
http :8080/actuator/health | jq '.components | to_entries[] | select(.value.status == "DOWN")'
```

**Solutions by Component:**

**PostgreSQL DOWN:**
```bash
docker logs notification-postgres
docker restart notification-postgres
```

**Redis DOWN:**
```bash
docker exec notification-redis redis-cli ping
docker restart notification-redis
```

**Kafka DOWN:**
```bash
docker logs notification-kafka
docker restart notification-kafka
```

### Issue: UNKNOWN_TOPIC_OR_PARTITION

**Symptom:** Warning in application logs during startup

**Solution:** This is normal. Kafka auto-creates topics on first message. Wait a few seconds.

### Verify Kafka Topics

```bash
# View all topics
docker exec notification-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092,localhost:9093,localhost:9094 \
  --list

# Check topic details
docker exec notification-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092,localhost:9093,localhost:9094 \
  --describe \
  --topic notifications.email
```

### Check Database State

```bash
# Connect to PostgreSQL
docker exec -it notification-postgres psql -U notification_user -d notification_db

# View recent notifications
SELECT id, channel, status, created_at 
FROM notifications 
ORDER BY created_at DESC 
LIMIT 10;

# View templates
SELECT name, channel, is_active FROM notification_templates;

# View users
SELECT id, email, phone FROM users;

# Exit
\q
```

### Check Redis Rate Limits

```bash
# Connect to Redis
docker exec -it notification-redis redis-cli

# View all rate limit keys
KEYS rate_limit:*

# Check specific user's rate limit
GET rate_limit:550e8400-e29b-41d4-a716-446655440001:EMAIL

# Clear all rate limits (for testing)
FLUSHALL

# Exit
exit
```

---

## Shell Variables for Testing

Set up shell variables for easier testing:

```bash
# User IDs
export USER_1="550e8400-e29b-41d4-a716-446655440001"
export USER_2="550e8400-e29b-41d4-a716-446655440002"

# Template IDs
export TEMPLATE_WELCOME="660e8400-e29b-41d4-a716-446655440001"
export TEMPLATE_ORDER="660e8400-e29b-41d4-a716-446655440002"
export TEMPLATE_OTP="660e8400-e29b-41d4-a716-446655440003"
export TEMPLATE_SHIPPED="660e8400-e29b-41d4-a716-446655440004"

# Base URL
export API_URL="http://localhost:8080"

# Now use in commands:
http POST $API_URL/api/v1/notifications \
  userId=$USER_1 \
  channel=EMAIL \
  templateName=welcome-email \
  templateVariables:='{"userName": "John Doe"}' \
  priority=HIGH
```

---

## Quick Reference

| Action | HTTPie Command |
|--------|----------------|
| Health check | `http :8080/api/v1/health` |
| Detailed health | `http :8080/actuator/health` |
| List templates | `http :8080/api/v1/templates` |
| Send email | `http POST :8080/api/v1/notifications userId=... channel=EMAIL templateName=... templateVariables:='{...}'` |
| Send SMS | `http POST :8080/api/v1/notifications userId=... channel=SMS content="..."` |
| Send push | `http POST :8080/api/v1/notifications userId=... channel=PUSH subject="..." content="..."` |
| Send in-app | `http POST :8080/api/v1/notifications userId=... channel=IN_APP subject="..." content="..."` |
| Bulk send | `http POST :8080/api/v1/notifications/bulk userIds:='[...]' channel=... content="..."` |
| Get notification | `http :8080/api/v1/notifications/{id}` |
| User notifications | `http :8080/api/v1/notifications/user/{userId} page==0 size==10` |
| Create template | `http POST :8080/api/v1/templates name=... channel=... bodyTemplate="..."` |
| Update template | `http PUT :8080/api/v1/templates/{id} name=... channel=... bodyTemplate="..."` |
| Delete template | `http DELETE :8080/api/v1/templates/{id}` |

---

## Additional Resources

| Resource | URL |
|----------|-----|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Kafka UI | http://localhost:8090 |
| Actuator | http://localhost:8080/actuator |
| OpenAPI Spec | http://localhost:8080/v3/api-docs |
| HTTPie Documentation | https://httpie.io/docs |

---

*Happy Testing with HTTPie! 🚀*
