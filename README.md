# 📬 Notification System

A multi-channel notification system built with Spring Boot, featuring email, SMS, push, and in-app notifications.

## 🌟 Features

- **Multi-Channel Support**: Email, SMS, Push, and In-App notifications
- **Rate Limiting**: Token bucket algorithm using Redis
- **Template System**: Reusable message templates with variable substitution
- **Async Processing**: Kafka-based message queue for reliable delivery
- **Retry Mechanism**: Exponential backoff for failed notifications
- **Priority Queue**: HIGH, MEDIUM, LOW priority processing
- **Event Deduplication**: Prevents duplicate notifications using Redis-backed event ID tracking
- **RESTful API**: Well-documented endpoints with Swagger UI

## 🏗️ Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   API Layer     │────▶│  Service Layer  │────▶│   Repository    │
│  (Controllers)  │     │ (Business Logic)│     │   (Database)    │
└─────────────────┘     └────────┬────────┘     └─────────────────┘
                                 │
                    ┌────────────┼────────────┐
                    ▼            ▼            ▼
              ┌──────────┐ ┌──────────┐ ┌──────────┐
              │  Kafka   │ │  Redis   │ │ Channels │
              │ (Queue)  │ │ (Cache)  │ │(Handlers)│
              └──────────┘ └──────────┘ └──────────┘
```

## 🛠️ Tech Stack

- **Java 17** - Modern LTS version
- **Spring Boot 3.2** - Application framework
- **PostgreSQL 15** - Primary database
- **Redis 7** - Rate limiting & caching
- **Apache Kafka** - Message queue
- **Docker Compose** - Local development

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Maven 3.8+

### 1. Start Infrastructure

```bash
# Start PostgreSQL, Redis, Kafka
docker-compose up -d
```

### 2. Run the Application

```bash
# Using Maven
./mvnw spring-boot:run

# Or build and run JAR
./mvnw clean package
java -jar target/notification-system-1.0.0.jar
```

### 3. Access the API

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/v3/api-docs
- **Health Check**: http://localhost:8080/api/v1/health
- **Kafka UI**: http://localhost:8090

## 📚 API Endpoints

### Notifications

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/notifications` | Send a notification |
| POST | `/api/v1/notifications/bulk` | Send bulk notifications |
| GET | `/api/v1/notifications/{id}` | Get notification by ID |
| GET | `/api/v1/notifications/user/{userId}` | Get user's notifications |
| GET | `/api/v1/notifications/user/{userId}/unread-count` | Get unread count |
| PATCH | `/api/v1/notifications/{id}/read` | Mark as read |
| PATCH | `/api/v1/notifications/user/{userId}/read-all` | Mark all as read |

## 🛡️ Event Deduplication

The notification system implements a robust **event deduplication mechanism** to prevent duplicate notifications from being sent, even in distributed environments where network issues or retries might cause the same event to be processed multiple times.

### How It Works

1. **Event ID Tracking**: Clients can provide a unique `eventId` in notification requests
2. **Redis-Based Storage**: Event IDs are stored in Redis with a configurable TTL (24 hours by default)
3. **Duplicate Detection**: Before processing any notification, the system checks if the event ID has been seen recently
4. **Graceful Handling**: If a duplicate is detected, the request is discarded with a clear error response

### API Usage

Include an `eventId` in your notification request:

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "eventId": "order-confirmation-12345",
    "subject": "Order Confirmed",
    "content": "Your order has been confirmed!"
  }'
```

### Benefits

- **Idempotent Operations**: Same event ID sent multiple times results in only one notification
- **Distributed Safety**: Works across multiple application instances
- **Configurable TTL**: Event IDs automatically expire to prevent memory bloat
- **Backward Compatible**: `eventId` is optional - existing clients continue to work
- **Fast Lookups**: Redis provides O(1) lookup performance

### Configuration

```yaml
notification:
  dedupe:
    ttl-seconds: 86400  # 24 hours
```

### Implementation Details

- **Service**: `DeduplicationService` handles event tracking
- **Storage**: Redis keys follow pattern `event:{eventId}`
- **TTL**: Configurable expiration prevents infinite growth
- **Thread-Safe**: Redis operations are atomic
- **Monitoring**: Logs duplicate detection for observability

### Templates

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/templates` | Create template |
| GET | `/api/v1/templates` | Get all templates |
| GET | `/api/v1/templates/{id}` | Get template by ID |
| GET | `/api/v1/templates/name/{name}` | Get template by name |
| PUT | `/api/v1/templates/{id}` | Update template |
| DELETE | `/api/v1/templates/{id}` | Delete template |

## 📝 Example Requests

### Send Email Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "priority": "HIGH",
    "subject": "Welcome!",
    "content": "Hello! Welcome to our platform."
  }'
```

### Send Using Template

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "templateName": "welcome-email",
    "templateVariables": {
      "userName": "John"
    }
  }'
```

### Create Template

```bash
curl -X POST http://localhost:8080/api/v1/templates \
  -H "Content-Type: application/json" \
  -d '{
    "name": "order-shipped",
    "channel": "EMAIL",
    "subjectTemplate": "Your order #{{orderId}} has shipped!",
    "bodyTemplate": "Hi {{userName}}, your order is on the way!"
  }'
```

## 📊 Database Schema

```sql
-- Core tables
users                    -- User information
user_preferences         -- Per-channel preferences
notification_templates   -- Reusable templates
notifications           -- Main notification table
```

## ⚙️ Configuration

Key settings in `application.yml`:

```yaml
notification:
  rate-limit:
    email: 10     # per hour
    sms: 5        # per hour
    push: 20      # per hour
    in-app: 100   # per hour
  dedupe:
    ttl-seconds: 86400  # 24 hours for event ID tracking
  retry:
    max-attempts: 3
    initial-delay: 60s
    multiplier: 5
```

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report
```

## 📈 Back-of-Envelope Calculations

See [plan.md](plan.md) for detailed capacity planning including:
- 10M notifications/day capacity
- ~116 notifications/second peak
- Storage and scaling estimates

## 🗂️ Project Structure

```
src/main/java/com/notification/
├── config/          # Configuration classes
├── controller/      # REST controllers
├── dto/             # Request/Response DTOs
├── exception/       # Custom exceptions
├── kafka/           # Kafka consumer
├── model/           # Entity classes
├── repository/      # JPA repositories
├── scheduler/       # Scheduled jobs
└── service/         # Business logic
    └── channel/     # Channel handlers
```

## 🔮 Future Enhancements

- [ ] Add authentication (OAuth2/JWT)
- [ ] Implement webhooks for delivery status
- [ ] Add support for message scheduling
- [ ] Implement multi-tenancy
- [ ] Add metrics with Prometheus/Grafana
- [ ] Support for attachments (email)
- [ ] A/B testing for templates

## 📄 License

MIT License - feel free to use for your projects!
