# Notification System - API Testing Guide

A comprehensive guide to testing the Notification System API endpoints. This guide walks you through each endpoint with examples, expected responses, and common use cases.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Health Check Endpoints](#health-check-endpoints)
4. [Template Endpoints](#template-endpoints)
5. [Notification Endpoints](#notification-endpoints)
6. [Testing Workflows](#testing-workflows)
7. [Error Handling](#error-handling)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### 1. Start the Infrastructure

Before testing, ensure all services are running:

```bash
# Start Docker containers (PostgreSQL, Redis, Kafka)
docker-compose up -d

# Verify containers are running
docker ps
```

Expected output:
```
CONTAINER ID   IMAGE                           STATUS          PORTS
xxxx           postgres:15                     Up 2 minutes    0.0.0.0:5432->5432/tcp
xxxx           redis:7-alpine                  Up 2 minutes    0.0.0.0:6379->6379/tcp
xxxx           confluentinc/cp-kafka:7.4.0     Up 2 minutes    0.0.0.0:9092->9092/tcp
xxxx           confluentinc/cp-zookeeper:7.4.0 Up 2 minutes    0.0.0.0:2181->2181/tcp
xxxx           provectuslabs/kafka-ui:latest   Up 2 minutes    0.0.0.0:8090->8080/tcp
```

### 2. Start the Application

```bash
# Run the Spring Boot application
mvn spring-boot:run
```

Wait for the startup message:
```
Started NotificationSystemApplication in X.XXX seconds
```

### 3. Available Testing Tools

| Tool | How to Use |
|------|------------|
| **cURL** | Command line (examples below) |
| **Postman** | Import `postman_collection.json` |
| **VS Code REST Client** | Open `api-requests.http` |
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **HTTPie** | `http GET localhost:8080/api/v1/health` |

---

## Quick Start

Test that everything is working with these three commands:

```bash
# 1. Health check
curl -s http://localhost:8080/api/v1/health | python3 -m json.tool

# 2. Get templates
curl -s http://localhost:8080/api/v1/templates | python3 -m json.tool

# 3. Send a notification
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "templateName": "welcome-email",
    "templateParams": {"userName": "John Doe"},
    "priority": "HIGH"
  }' | python3 -m json.tool
```

---

## Health Check Endpoints

### 1. Basic Health Check

**Purpose:** Verify the API is running and responding.

```bash
curl -s http://localhost:8080/api/v1/health | python3 -m json.tool
```

**Expected Response:**
```json
{
    "success": true,
    "message": "Service is healthy",
    "data": "OK",
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

### 2. Detailed Health Check (Actuator)

**Purpose:** Check health of all dependencies (DB, Redis, Kafka).

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

**Expected Response:**
```json
{
    "status": "UP",
    "components": {
        "db": { "status": "UP" },
        "redis": { "status": "UP" },
        "kafka": { "status": "UP" }
    }
}
```

---

## Template Endpoints

Templates are pre-defined message formats with placeholders for dynamic content.

### Pre-seeded Templates

The database comes with these templates:

| Name | Channel | Description |
|------|---------|-------------|
| `welcome-email` | EMAIL | Welcome message for new users |
| `order-confirmation` | EMAIL | Order confirmation |
| `otp-sms` | SMS | OTP verification code |
| `order-shipped` | PUSH | Order shipment notification |

### 1. Get All Templates

**Purpose:** List all available notification templates.

```bash
curl -s http://localhost:8080/api/v1/templates | python3 -m json.tool
```

**Expected Response:**
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
            "bodyTemplate": "Hi {{userName}},\n\nThank you for joining...",
            "active": true
        }
        // ... more templates
    ],
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

### 2. Get Template by Name

**Purpose:** Retrieve a specific template by its unique name.

```bash
curl -s http://localhost:8080/api/v1/templates/name/welcome-email | python3 -m json.tool
```

### 3. Get Template by ID

**Purpose:** Retrieve a specific template by its UUID.

```bash
curl -s http://localhost:8080/api/v1/templates/660e8400-e29b-41d4-a716-446655440001 | python3 -m json.tool
```

### 4. Create a New Template

**Purpose:** Create a custom notification template.

```bash
curl -s -X POST http://localhost:8080/api/v1/templates \
  -H "Content-Type: application/json" \
  -d '{
    "name": "password-reset",
    "channel": "EMAIL",
    "subjectTemplate": "Reset Your Password",
    "bodyTemplate": "Hi {{userName}},\n\nClick here to reset: {{resetLink}}\n\nExpires in {{expiryTime}}.",
    "active": true
  }' | python3 -m json.tool
```

**Template Variables:**
- Use `{{variableName}}` syntax
- Variables are replaced with values from `templateParams` when sending notifications

**Expected Response:**
```json
{
    "success": true,
    "message": "Template created",
    "data": {
        "id": "generated-uuid-here",
        "name": "password-reset",
        "channel": "EMAIL",
        "subjectTemplate": "Reset Your Password",
        "bodyTemplate": "Hi {{userName}},\n\nClick here to reset: {{resetLink}}\n\nExpires in {{expiryTime}}.",
        "active": true
    },
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

### 5. Update a Template

**Purpose:** Modify an existing template.

```bash
curl -s -X PUT http://localhost:8080/api/v1/templates/660e8400-e29b-41d4-a716-446655440001 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "welcome-email",
    "channel": "EMAIL",
    "subjectTemplate": "Welcome to Our Platform, {{userName}}! 🎉",
    "bodyTemplate": "Hi {{userName}},\n\nWelcome aboard! We are excited to have you.",
    "active": true
  }' | python3 -m json.tool
```

### 6. Delete a Template

**Purpose:** Soft-delete a template (sets `active` to false).

```bash
curl -s -X DELETE http://localhost:8080/api/v1/templates/660e8400-e29b-41d4-a716-446655440004 | python3 -m json.tool
```

---

## Notification Endpoints

### Pre-seeded Users

The database has these test users:

| User ID | Email | Phone |
|---------|-------|-------|
| `550e8400-e29b-41d4-a716-446655440001` | john.doe@example.com | +1234567890 |
| `550e8400-e29b-41d4-a716-446655440002` | jane.smith@example.com | +0987654321 |

### Channels & Priorities

**Channels:**
| Channel | Description |
|---------|-------------|
| `EMAIL` | Email notifications |
| `SMS` | Text messages |
| `PUSH` | Mobile push notifications |
| `IN_APP` | In-application notifications |

**Priorities:**
| Priority | Description | Use Case |
|----------|-------------|----------|
| `LOW` | Non-urgent | Marketing, updates |
| `MEDIUM` | Normal priority | Order updates |
| `HIGH` | Important | Account alerts |
| `CRITICAL` | Immediate delivery | OTP, security alerts |

---

### 1. Send Notification with Template

**Purpose:** Send a notification using a pre-defined template.

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "templateName": "welcome-email",
    "templateParams": {
        "userName": "John Doe"
    },
    "priority": "HIGH"
  }' | python3 -m json.tool
```

**Request Body Explained:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | UUID | ✅ | Target user's ID |
| `channel` | String | ✅ | EMAIL, SMS, PUSH, or IN_APP |
| `templateName` | String | ⚠️ | Template name (required if no content) |
| `templateParams` | Object | ❌ | Values for template placeholders |
| `priority` | String | ❌ | LOW, MEDIUM, HIGH, CRITICAL (default: MEDIUM) |

**Expected Response:**
```json
{
    "success": true,
    "message": "Notification queued successfully",
    "data": {
        "id": "feaad8eb-7fd3-4ddf-90ba-78142cf1c83f",
        "userId": "550e8400-e29b-41d4-a716-446655440001",
        "channel": "EMAIL",
        "priority": "HIGH",
        "subject": "Welcome to Our Platform, John Doe!",
        "content": "Hi John Doe,\n\nThank you for joining...",
        "status": "PENDING",
        "retryCount": 0,
        "createdAt": "2026-01-04T10:30:00.000000+05:30"
    },
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

---

### 2. Send Notification with Custom Content

**Purpose:** Send a notification without using a template.

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "subject": "Custom Subject Line",
    "content": "This is a custom message without using any template.",
    "priority": "MEDIUM"
  }' | python3 -m json.tool
```

---

### 3. Send SMS Notification (OTP Example)

**Purpose:** Send a time-sensitive OTP via SMS.

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "SMS",
    "templateName": "otp-sms",
    "templateParams": {
        "otpCode": "847293"
    },
    "priority": "CRITICAL"
  }' | python3 -m json.tool
```

---

### 4. Send Push Notification

**Purpose:** Send a push notification to mobile device.

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440002",
    "channel": "PUSH",
    "templateName": "order-shipped",
    "templateParams": {
        "orderId": "ORD-12345",
        "trackingUrl": "https://track.example.com/ORD-12345"
    },
    "priority": "HIGH"
  }' | python3 -m json.tool
```

---

### 5. Send In-App Notification

**Purpose:** Send a notification to user's in-app notification center.

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "IN_APP",
    "subject": "New Feature Available!",
    "content": "Check out our new dashboard with enhanced analytics.",
    "priority": "LOW"
  }' | python3 -m json.tool
```

---

### 6. Send Bulk Notifications

**Purpose:** Send the same notification to multiple users at once.

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "userIds": [
        "550e8400-e29b-41d4-a716-446655440001",
        "550e8400-e29b-41d4-a716-446655440002"
    ],
    "channel": "EMAIL",
    "templateName": "order-confirmation",
    "templateParams": {
        "orderId": "ORD-99999",
        "orderTotal": "$149.99",
        "userName": "Valued Customer"
    },
    "priority": "HIGH"
  }' | python3 -m json.tool
```

**Expected Response:**
```json
{
    "success": true,
    "message": "Bulk notifications queued",
    "data": {
        "totalRequested": 2,
        "successCount": 2,
        "failedCount": 0,
        "notificationIds": [
            "uuid-1",
            "uuid-2"
        ]
    },
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

---

### 7. Get Notification by ID

**Purpose:** Retrieve details of a specific notification.

```bash
# Replace with actual notification ID from previous response
curl -s http://localhost:8080/api/v1/notifications/feaad8eb-7fd3-4ddf-90ba-78142cf1c83f | python3 -m json.tool
```

**Expected Response:**
```json
{
    "success": true,
    "message": "Notification retrieved",
    "data": {
        "id": "feaad8eb-7fd3-4ddf-90ba-78142cf1c83f",
        "userId": "550e8400-e29b-41d4-a716-446655440001",
        "channel": "EMAIL",
        "priority": "HIGH",
        "subject": "Welcome to Our Platform, John Doe!",
        "content": "Hi John Doe,...",
        "status": "SENT",
        "retryCount": 0,
        "createdAt": "2026-01-04T10:30:00.000000+05:30",
        "sentAt": "2026-01-04T10:30:01.000000+05:30"
    },
    "timestamp": "2026-01-04T10:30:05.000000+05:30"
}
```

**Notification Statuses:**
| Status | Description |
|--------|-------------|
| `PENDING` | Queued, waiting to be processed |
| `SENT` | Successfully sent to delivery channel |
| `DELIVERED` | Confirmed delivered to user |
| `FAILED` | Delivery failed (will retry if retryCount < 3) |
| `READ` | User has read the notification |

---

### 8. Get User's Notifications (Paginated)

**Purpose:** Retrieve all notifications for a specific user.

```bash
curl -s "http://localhost:8080/api/v1/notifications/user/550e8400-e29b-41d4-a716-446655440001?page=0&size=10" | python3 -m json.tool
```

**Query Parameters:**
| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | 0 | Page number (0-indexed) |
| `size` | 20 | Items per page |
| `status` | - | Filter by status (optional) |

---

### 9. Get User's Notifications Filtered by Status

**Purpose:** Get only notifications with a specific status.

```bash
# Get only PENDING notifications
curl -s "http://localhost:8080/api/v1/notifications/user/550e8400-e29b-41d4-a716-446655440001?page=0&size=10&status=PENDING" | python3 -m json.tool

# Get only FAILED notifications
curl -s "http://localhost:8080/api/v1/notifications/user/550e8400-e29b-41d4-a716-446655440001?page=0&size=10&status=FAILED" | python3 -m json.tool
```

---

## Testing Workflows

### Workflow 1: Complete Email Notification Flow

Test the entire flow from template to delivery:

```bash
# Step 1: Check available templates
curl -s http://localhost:8080/api/v1/templates | python3 -m json.tool

# Step 2: Send a notification
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "templateName": "welcome-email",
    "templateParams": {"userName": "Test User"},
    "priority": "HIGH"
  }')

echo $RESPONSE | python3 -m json.tool

# Step 3: Extract notification ID and check status
NOTIFICATION_ID=$(echo $RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['id'])")
echo "Notification ID: $NOTIFICATION_ID"

# Step 4: Wait a moment and check the notification status
sleep 2
curl -s "http://localhost:8080/api/v1/notifications/$NOTIFICATION_ID" | python3 -m json.tool
```

### Workflow 2: Create and Use Custom Template

```bash
# Step 1: Create a new template
curl -s -X POST http://localhost:8080/api/v1/templates \
  -H "Content-Type: application/json" \
  -d '{
    "name": "payment-success",
    "channel": "EMAIL",
    "subjectTemplate": "Payment Received - {{amount}}",
    "bodyTemplate": "Hi {{userName}},\n\nWe received your payment of {{amount}}.\n\nTransaction ID: {{transactionId}}\nDate: {{paymentDate}}\n\nThank you!",
    "active": true
  }' | python3 -m json.tool

# Step 2: Use the new template
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "templateName": "payment-success",
    "templateParams": {
        "userName": "John Doe",
        "amount": "$99.99",
        "transactionId": "TXN-123456",
        "paymentDate": "January 4, 2026"
    },
    "priority": "HIGH"
  }' | python3 -m json.tool
```

### Workflow 3: Test Rate Limiting

Send multiple requests quickly to test rate limiting:

```bash
# Send 5 rapid requests (rate limit is 10 per minute per user)
for i in {1..5}; do
  echo "Request $i:"
  curl -s -X POST http://localhost:8080/api/v1/notifications \
    -H "Content-Type: application/json" \
    -d '{
      "userId": "550e8400-e29b-41d4-a716-446655440001",
      "channel": "EMAIL",
      "subject": "Test '$i'",
      "content": "Rate limit test message '$i'",
      "priority": "LOW"
    }' | python3 -c "import sys, json; d=json.load(sys.stdin); print(f\"  Success: {d['success']}, Message: {d['message']}\")"
done
```

### Workflow 4: Test All Channels

```bash
# Test each channel
for channel in EMAIL SMS PUSH IN_APP; do
  echo "Testing $channel channel:"
  curl -s -X POST http://localhost:8080/api/v1/notifications \
    -H "Content-Type: application/json" \
    -d '{
      "userId": "550e8400-e29b-41d4-a716-446655440001",
      "channel": "'$channel'",
      "subject": "Test '$channel' Notification",
      "content": "This is a test message for the '$channel' channel.",
      "priority": "MEDIUM"
    }' | python3 -c "import sys, json; d=json.load(sys.stdin); print(f\"  Status: {d['success']}, ID: {d.get('data', {}).get('id', 'N/A')}\")"
  echo ""
done
```

---

## Error Handling

### Common Error Responses

#### 1. Missing Required Fields

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL"
  }' | python3 -m json.tool
```

**Response:**
```json
{
    "success": false,
    "message": "Either templateName or content must be provided",
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

#### 2. Invalid User ID

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "non-existent-user-id",
    "channel": "EMAIL",
    "content": "Test",
    "priority": "HIGH"
  }' | python3 -m json.tool
```

**Response:**
```json
{
    "success": false,
    "message": "User not found with id: non-existent-user-id",
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

#### 3. Template Not Found

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "templateName": "non-existent-template",
    "priority": "HIGH"
  }' | python3 -m json.tool
```

**Response:**
```json
{
    "success": false,
    "message": "Template not found: non-existent-template",
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

#### 4. Rate Limit Exceeded

**Response:**
```json
{
    "success": false,
    "message": "Rate limit exceeded. Please try again later.",
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

#### 5. Notification Not Found

```bash
curl -s http://localhost:8080/api/v1/notifications/00000000-0000-0000-0000-000000000000 | python3 -m json.tool
```

**Response:**
```json
{
    "success": false,
    "message": "Notification not found with id: 00000000-0000-0000-0000-000000000000",
    "timestamp": "2026-01-04T10:30:00.000000+05:30"
}
```

---

## Troubleshooting

### Issue: Connection Refused

**Symptom:**
```
curl: (7) Failed to connect to localhost port 8080: Connection refused
```

**Solutions:**
1. Check if the application is running: `ps aux | grep spring-boot`
2. Start the application: `mvn spring-boot:run`
3. Check application logs: `tail -100 /tmp/app.log`

### Issue: Database Connection Error

**Symptom:**
```json
{
    "success": false,
    "message": "An unexpected error occurred. Please try again later."
}
```

**Solutions:**
1. Check Docker containers: `docker ps`
2. Restart containers: `docker-compose down && docker-compose up -d`
3. Wait 10-15 seconds for services to initialize

### Issue: Kafka Warnings

**Symptom:**
```
WARN: Error while fetching metadata: UNKNOWN_TOPIC_OR_PARTITION
```

**Solution:** This is normal during startup. Kafka auto-creates topics on first message. Wait a few seconds and the warnings will stop.

### Issue: Rate Limit Hit During Testing

**Solution:** 
1. Wait 60 seconds for the rate limit window to reset
2. Use different user IDs for testing
3. Reduce request frequency

### Checking Application Logs

```bash
# If running in background
tail -f /tmp/app.log

# Filter for errors only
grep -i "error\|exception" /tmp/app.log

# Check Kafka consumer activity
grep -i "kafka\|consumer" /tmp/app.log
```

### Checking Database State

```bash
# Connect to PostgreSQL
docker exec -it notification-postgres psql -U notification_user -d notification_db

# View notifications
SELECT id, channel, status, created_at FROM notifications ORDER BY created_at DESC LIMIT 10;

# View templates
SELECT name, channel, active FROM notification_templates;

# View users
SELECT id, email, phone FROM users;

# Exit
\q
```

### Checking Redis State

```bash
# Connect to Redis
docker exec -it notification-redis redis-cli

# View all keys
KEYS *

# Check rate limit for a user
GET rate_limit:550e8400-e29b-41d4-a716-446655440001

# Exit
exit
```

---

## Additional Resources

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Kafka UI:** http://localhost:8090
- **Actuator Endpoints:** http://localhost:8080/actuator
- **OpenAPI Spec:** http://localhost:8080/v3/api-docs

---

## Quick Reference Card

| Action | Command |
|--------|---------|
| Health Check | `curl localhost:8080/api/v1/health` |
| List Templates | `curl localhost:8080/api/v1/templates` |
| Send Email | `curl -X POST localhost:8080/api/v1/notifications -H "Content-Type: application/json" -d '{"userId":"550e8400-e29b-41d4-a716-446655440001","channel":"EMAIL","templateName":"welcome-email","templateParams":{"userName":"John"},"priority":"HIGH"}'` |
| Send Bulk | `curl -X POST localhost:8080/api/v1/notifications/bulk -H "Content-Type: application/json" -d '{"userIds":["user1","user2"],"channel":"EMAIL","content":"Hello","priority":"HIGH"}'` |
| Get Notification | `curl localhost:8080/api/v1/notifications/{id}` |
| Get User Notifications | `curl "localhost:8080/api/v1/notifications/user/{userId}?page=0&size=10"` |
| Create Template | `curl -X POST localhost:8080/api/v1/templates -H "Content-Type: application/json" -d '{"name":"test","channel":"EMAIL","subjectTemplate":"Hello","bodyTemplate":"World","active":true}'` |

---

*Happy Testing! 🚀*
