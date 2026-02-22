# Notification System

A scalable multi-channel notification platform built with Spring Boot, PostgreSQL, Redis, and Kafka. Supports async delivery, rate limiting, templates, retries, event deduplication, and observability with Prometheus + Grafana.

---

## Table of Contents

| # | Section | What you'll learn |
|---|---------|-------------------|
| 1 | [Overview](#1-overview) | What this system does and what it's built with |
| 2 | [Getting Started](#2-getting-started) | Prerequisites, setup, and first health check |
| 3 | [Using the API](#3-using-the-api) | Endpoints, example requests, and test data |
| 4 | [Architecture & Design](#4-architecture--design) | How requests flow, Kafka topics, retry/dedup/rate-limit |
| 5 | [Testing & Observability](#5-testing--observability) | Unit tests, k6 stress tests, Prometheus + Grafana |
| 6 | [Reference](#6-reference) | Configuration, troubleshooting, related docs |

---

# 1. Overview

## What It Does

A notification service that accepts requests via REST API, persists them, and asynchronously delivers them through the appropriate channel (email, SMS, push, or in-app). Along the way it handles deduplication, rate limiting, retries, and templating.

## Core Features

| Feature | How it works |
|---------|-------------|
| Multi-channel delivery | `EMAIL`, `SMS`, `PUSH`, `IN_APP` — each with its own Kafka topic and handler |
| Async processing | Kafka decouples the API from delivery; the API returns `201` immediately |
| Rate limiting | Redis token-bucket counters per user per channel |
| Template rendering | Named templates with `{{variable}}` substitution |
| Retry with backoff | Failed deliveries retry up to 3 times with `5^n` minute delays |
| Event deduplication | Optional `eventId` checked against Redis with 24h TTL |
| Priority support | `HIGH`, `MEDIUM`, `LOW` — controls processing order |
| Observability | Prometheus metrics, Grafana dashboards, Spring Actuator |
| Load testing | k6 scripts for stress, spike, and heap-pressure scenarios |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language & Framework | Java 21, Spring Boot 3.2.x |
| Database | PostgreSQL 15 (via Spring Data JPA + Hibernate) |
| Cache & Coordination | Redis 7 (caching, rate limiting, deduplication) |
| Message Broker | Apache Kafka (3 brokers) |
| Schema Migrations | Flyway |
| Metrics | Spring Actuator + Micrometer + Prometheus |
| Dashboards | Grafana (pre-provisioned) |
| Infrastructure | Docker Compose |
| Load Testing | k6 |

---

# 2. Getting Started

## Prerequisites

- Java 21+
- Maven 3.8+
- Docker + Docker Compose
- k6 (optional — only needed for load testing)

## Quick Start

```bash
# 1. Start infrastructure (Postgres, Redis, Kafka, Prometheus, Grafana)
docker-compose up -d

# 2. Run the application
mvn spring-boot:run

# 3. Verify everything is healthy
curl -s http://localhost:8080/api/v1/health
curl -s http://localhost:8080/actuator/health
```

## Service URLs

| What | URL | Notes |
|------|-----|-------|
| **Application API** | http://localhost:8080 | REST endpoints |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | Interactive API docs |
| **Grafana** | http://localhost:3000 | Dashboards (admin/admin) |
| **Prometheus** | http://localhost:9090 | Metrics & targets |
| **Kafka UI** | http://localhost:8090 | Topic browser |
| **Health Check** | http://localhost:8080/api/v1/health | Basic health |
| **Detailed Health** | http://localhost:8080/api/v1/health/detailed | DB / Redis / Kafka status |
| **Metrics (Prometheus format)** | http://localhost:8080/actuator/prometheus | Scrape endpoint |

**Infrastructure ports:** PostgreSQL `5432` · Redis `6379` · Kafka brokers `9092`, `9093`, `9094`

---

# 3. Using the API

## Notifications

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/notifications` | Send a single notification |
| `POST` | `/api/v1/notifications/bulk` | Send bulk notifications |
| `GET` | `/api/v1/notifications/{id}` | Get notification by ID |
| `GET` | `/api/v1/notifications/user/{userId}` | User inbox (paginated) |
| `GET` | `/api/v1/notifications/user/{userId}/unread-count` | Unread count |
| `PATCH` | `/api/v1/notifications/{id}/read` | Mark as read |
| `PATCH` | `/api/v1/notifications/user/{userId}/read-all` | Mark all as read |

### Example: Send with direct content

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "priority": "HIGH",
    "subject": "Welcome!",
    "content": "Hello and welcome to our platform.",
    "eventId": "welcome-evt-1001"
  }'
```

### Example: Send with a template

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "channel": "EMAIL",
    "templateName": "welcome-email",
    "templateVariables": { "userName": "John" }
  }'
```

## Templates

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/templates` | Create template |
| `GET` | `/api/v1/templates` | List all (optional `?channel=` filter) |
| `GET` | `/api/v1/templates/{id}` | Get by ID |
| `GET` | `/api/v1/templates/name/{name}` | Get by name |
| `PUT` | `/api/v1/templates/{id}` | Update |
| `DELETE` | `/api/v1/templates/{id}` | Soft-delete |

## Users

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/users` | Create user (if not exists) |
| `GET` | `/api/v1/users` | List all |
| `GET` | `/api/v1/users/email/{email}` | Find by email (cached) |
| `GET` | `/api/v1/users/phone/{phone}` | Find by phone (cached) |
| `GET` | `/api/v1/users/push-eligible` | Users with device tokens |

## Debug & Health

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/v1/debug/alloc?mb={n}` | Allocate `n` MB on JVM heap (load-testing only) |
| `POST` | `/api/v1/debug/clear` | Release allocations and hint GC |
| `GET` | `/api/v1/health` | Basic health |
| `GET` | `/api/v1/health/detailed` | Dependency health (DB, Redis, Kafka) |
| `GET` | `/actuator/prometheus` | Prometheus scrape endpoint |

## Seeded Test Data

The migration seeds three users and four templates so you can test immediately:

**Users:**

| User ID | Email | Phone |
|---------|-------|-------|
| `550e8400-e29b-41d4-a716-446655440001` | john@example.com | +1234567890 |
| `550e8400-e29b-41d4-a716-446655440002` | jane@example.com | +1987654321 |
| `550e8400-e29b-41d4-a716-446655440003` | bob@example.com | +1555555555 |

**Templates:**

| Name | Channel |
|------|---------|
| `welcome-email` | EMAIL |
| `order-confirmation` | EMAIL |
| `otp-sms` | SMS |
| `order-shipped` | PUSH |

---

# 4. Architecture & Design

## Request Flow

```text
                        SYNCHRONOUS (API thread)
                        ────────────────────────
Client ──► REST Controller ──► NotificationService
                                  ├── UserService.findById()   ← Redis cache hit (~0.5ms)
                                  ├── DeduplicationService     ← Redis event:{eventId} check
                                  ├── RateLimiterService       ← Redis token-bucket check
                                  ├── TemplateService          ← render if templateName given
                                  ├── NotificationRepository   ← INSERT as PENDING
                                  └── KafkaTemplate.send()     ← publish ID to channel topic
                                       │
                              Return 201 Created
                                       │
                        ASYNCHRONOUS (Kafka consumer thread)
                        ────────────────────────────────────
                              Kafka Consumer
                                  ├── Load notification from DB
                                  ├── Mark PROCESSING
                                  ├── ChannelDispatcher ──► ChannelHandler (email/sms/push/in-app)
                                  ├── Mark SENT  ──or──  scheduleRetry / FAILED
                                  └── Acknowledge offset
```

## Notification Lifecycle

```text
PENDING ──► PROCESSING ──► SENT ──► DELIVERED ──► READ
                │                                   ▲
                ▼                                   │
            FAILED ◄── (retries exhausted)     (in-app only)
                │
                ▼
          RETRY (5^n min backoff, max 3 attempts)
```

## Kafka Topic Design

Each channel gets its own topic so failures in one channel don't block others, and each can scale independently.

| Channel | Topic | Partitions |
|---------|-------|------------|
| EMAIL | `notifications.email` | 8 |
| SMS | `notifications.sms` | 4 |
| PUSH | `notifications.push` | 8 |
| IN_APP | `notifications.in-app` | 6 |
| Dead Letter Queue | `notifications.dlq` | 1 |

**Why per-channel topics?**
- A slow email provider won't delay push notifications
- Each channel can have different consumer concurrency
- Easier to monitor and debug per-channel lag

## Deduplication

Prevents the same business event from creating duplicate notifications.

| Aspect | Detail |
|--------|--------|
| Trigger | Optional `eventId` field on the request |
| Storage | Redis key `event:{eventId}` |
| TTL | 24 hours (86400s, configurable) |
| Behavior | Duplicate eventId → request rejected gracefully |

## Rate Limiting

Prevents notification spam per user per channel.

| Aspect | Detail |
|--------|--------|
| Algorithm | Token-bucket via Redis `INCR` + `TTL` |
| Key format | `ratelimit:{userId}:{channel}` |
| Window | 1 hour (3600s) |
| Limits | EMAIL: 10/hr, SMS: 5/hr, PUSH: 20/hr, IN_APP: 100/hr |
| Behavior | Over-limit → `429 Too Many Requests` |

## Retry Strategy

| Aspect | Detail |
|--------|--------|
| Formula | `5^retryCount` minutes (5 min, 25 min) |
| Max retries | 3 (then status → `FAILED`) |
| Scheduler | `@Scheduled` every 60s scans for due retries |
| Stuck detection | Notifications stuck in `PROCESSING` > 10 min get retried |

## Database Schema

Migration file: `src/main/resources/db/migration/V1__init_schema.sql`

| Table | Purpose |
|-------|---------|
| `users` | User profiles (email, phone, device token) |
| `user_preferences` | Per-channel opt-in/opt-out |
| `notification_templates` | Reusable message templates |
| `notifications` | Every notification with status, retry count, timestamps |

Indexes cover: user inbox pagination, status/channel filtering, retry scheduling, and user lookups.

## Project Structure

```text
src/main/java/com/notification/
├── config/          # Spring / Kafka / Redis / OpenAPI configuration
├── controller/      # REST endpoints (Notification, Template, User, Health, Debug)
├── dto/             # Request / Response contracts
├── exception/       # Global error handler and custom exceptions
├── kafka/           # Kafka consumers (one @KafkaListener per channel)
├── model/           # JPA entities and enums (Notification, User, Template)
├── repository/      # Spring Data JPA repositories
├── scheduler/       # RetryScheduler (@Scheduled background job)
└── service/         # Business logic + channel handlers (Strategy Pattern)

src/main/resources/
├── application.yml                      # All config (server, DB, Redis, Kafka, tuning)
├── db/migration/V1__init_schema.sql     # Flyway migration + seed data
└── static/index.html                    # Landing page

monitoring/
├── prometheus/prometheus.yml                          # Scrape config
└── grafana/
    ├── dashboards/notification-system-overview.json   # Pre-built dashboard
    └── provisioning/                                  # Auto-provisioning

stress-test/k6/
├── stress-test.js   # Sustained load (ramps to 10k VUs)
├── spike-test.js    # Sudden burst test
└── heap-test.js     # JVM memory pressure test
```

---

# 5. Testing & Observability

## Unit Tests

```bash
mvn test                  # run all tests
mvn test jacoco:report    # run tests + generate coverage report
```

## Stress & Spike Testing (k6)

Three test profiles in `stress-test/k6/`:

| Script | What it tests | Peak VUs |
|--------|--------------|----------|
| `stress-test.js` | Sustained throughput and latency | 10,000 |
| `spike-test.js` | Sudden burst and recovery | 1,000 |
| `heap-test.js` | JVM heap growth under pressure | — |

**Thresholds:** p95 < 750ms, error rate < 5%

```bash
# Basic stress test (GET /)
k6 run stress-test/k6/stress-test.js

# Target the notification API with POST
BASE_URL=http://localhost:8080 \
TARGET_PATH=/api/v1/notifications \
METHOD=POST \
k6 run stress-test/k6/stress-test.js
```

**Env vars:** `BASE_URL`, `TARGET_PATH`, `METHOD` (`GET`/`POST`), `PAYLOAD` (JSON string)

> **Tip:** Keep Grafana open while running k6 to see spike impact in real time.

## Monitoring Stack (Prometheus + Grafana)

```text
Application (Micrometer) ──► Prometheus (scrape every 15s) ──► Grafana (dashboards)
```

**Services included in Docker Compose:**

| Service | Purpose |
|---------|---------|
| Prometheus | Scrapes `/actuator/prometheus` |
| Grafana | Pre-provisioned dashboard |
| PostgreSQL exporter | DB metrics |
| Redis exporter | Cache metrics |
| Kafka exporter | Broker/consumer metrics |

**Pre-built dashboard panels:**
- Request rate (RPS) and p50/p95/p99 latency
- 5xx error rate
- JVM heap usage and GC pressure
- HikariCP connection pool utilization
- PostgreSQL / Redis / Kafka health

**Note:** `monitoring/prometheus/prometheus.yml` uses `host.docker.internal:8080`. On Linux, replace with host IP or run the app in the same Docker network.

---

# 6. Reference

## Configuration Highlights

Main config: `src/main/resources/application.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true

notification:
  dedupe:
    ttl-seconds: 86400
  retry:
    max-attempts: 3
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Grafana not opening on `:3000` | Run `docker-compose ps grafana prometheus` and check `curl http://localhost:3000/api/health` |
| Prometheus dashboard empty | Verify app is on `:8080`, check `curl http://localhost:8080/actuator/prometheus`, check targets at `http://localhost:9090/targets` |
| `./mvnw` not found | Use `mvn` instead (wrapper not committed) |
| Docker Compose `version` warning | Informational only — Compose ignores it |
| k6 stress test errors | Check endpoint path and POST payload schema |

## Related Documentation

| Document | Content |
|----------|---------|
| [API_TESTING_GUIDE.md](API_TESTING_GUIDE.md) | Detailed API testing walkthrough |
| [DATA_FLOW_DOCUMENTATION.md](DATA_FLOW_DOCUMENTATION.md) | End-to-end data flow diagrams |
| [DOCKER_GUIDE.md](DOCKER_GUIDE.md) | Docker Compose setup and config |
| [KAFKA_GUIDE.md](KAFKA_GUIDE.md) | Kafka topic design and consumer tuning |
| [PROJECT_WALKTHROUGH.md](PROJECT_WALKTHROUGH.md) | Code-level walkthrough |
| [TECHNICAL_DISCUSSION_SUMMARY.md](TECHNICAL_DISCUSSION_SUMMARY.md) | Design decisions and trade-offs |
| [plan.md](plan.md) | Original project plan |

## Future Enhancements

- Authentication (OAuth2 / JWT)
- Webhooks for delivery status callbacks
- Scheduled / delayed notifications
- Multi-tenancy
- Grafana alerting rules (latency, error rate, Kafka lag)
- Email attachments
- A/B testing for notification templates

---

## License

MIT License
