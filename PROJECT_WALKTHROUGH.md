# Project Walkthrough - Notification System

A comprehensive guide to understanding, reading, and navigating the Notification System codebase. This guide is designed for beginners and explains every file, its purpose, and how all the pieces connect.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [How to Read This Project](#how-to-read-this-project)
3. [Project Structure](#project-structure)
4. [Reading Order (Recommended)](#reading-order-recommended)
5. [Layer-by-Layer Breakdown](#layer-by-layer-breakdown)
   - [1. Entry Point](#1-entry-point)
   - [2. Configuration Layer](#2-configuration-layer)
   - [3. Model Layer (Entities & Enums)](#3-model-layer-entities--enums)
   - [4. Repository Layer (Database Access)](#4-repository-layer-database-access)
   - [5. DTO Layer (Data Transfer Objects)](#5-dto-layer-data-transfer-objects)
   - [6. Service Layer (Business Logic)](#6-service-layer-business-logic)
   - [7. Controller Layer (API Endpoints)](#7-controller-layer-api-endpoints)
   - [8. Kafka Layer (Message Queue)](#8-kafka-layer-message-queue)
   - [9. Exception Handling](#9-exception-handling)
   - [10. Scheduler Layer](#10-scheduler-layer)
6. [Request Flow Diagram](#request-flow-diagram)
7. [Database Schema](#database-schema)
8. [Configuration Files](#configuration-files)
9. [Key Design Patterns Used](#key-design-patterns-used)
10. [How Components Connect](#how-components-connect)
11. [Quick Reference](#quick-reference)

---

## Project Overview

### What This Project Does
A **Notification System** that can send notifications via multiple channels (Email, SMS, Push, In-App) with features like:
- Template-based messaging
- Rate limiting (prevent spam)
- Async processing via Kafka
- Retry mechanism for failed notifications
- Multiple priority levels

### Tech Stack
| Technology | Purpose |
|------------|---------|
| **Java 21** | Programming language |
| **Spring Boot 3.2** | Application framework |
| **PostgreSQL** | Database for storing data |
| **Redis** | Caching and rate limiting |
| **Apache Kafka** | Message queue for async processing |
| **Flyway** | Database migrations |
| **Swagger/OpenAPI** | API documentation |

---

## How to Read This Project

### For Beginners: The "Onion" Approach

Think of this project like an **onion with layers**. Start from the outside (what users see) and work inward (how it works):

```
┌─────────────────────────────────────────────────────────┐
│                    Controller Layer                      │  ← User interacts here (API)
│  ┌─────────────────────────────────────────────────┐    │
│  │                 Service Layer                    │    │  ← Business logic lives here
│  │  ┌─────────────────────────────────────────┐    │    │
│  │  │            Repository Layer              │    │    │  ← Database access
│  │  │  ┌─────────────────────────────────┐    │    │    │
│  │  │  │         Model Layer             │    │    │    │  ← Data structures
│  │  │  └─────────────────────────────────┘    │    │    │
│  │  └─────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### Tips for Reading Code

1. **Start with the API** - Look at controllers to understand what the system can do
2. **Follow a request** - Pick one endpoint and trace it through all layers
3. **Read comments** - Every file has detailed comments explaining concepts
4. **Ignore imports** - Focus on the logic, not the import statements
5. **Use IDE features** - Ctrl+Click (or Cmd+Click) to jump to definitions

---

## Project Structure

```
notification-system/
│
├── 📄 pom.xml                      # Maven config (dependencies)
├── 📄 docker-compose.yml           # Infrastructure setup
├── 📄 README.md                    # Project overview
├── 📄 API_TESTING_GUIDE.md         # How to test APIs
├── 📄 DOCKER_GUIDE.md              # Docker commands guide
├── 📄 PROJECT_WALKTHROUGH.md       # This file!
│
└── src/
    ├── main/
    │   ├── java/com/notification/
    │   │   │
    │   │   ├── 📄 NotificationSystemApplication.java   # 🚀 Entry point
    │   │   │
    │   │   ├── 📁 config/                # ⚙️ Configuration
    │   │   │   ├── KafkaConfig.java      # Kafka setup
    │   │   │   ├── RedisConfig.java      # Redis setup
    │   │   │   └── OpenApiConfig.java    # Swagger setup
    │   │   │
    │   │   ├── 📁 model/                 # 📊 Data models
    │   │   │   ├── entity/               # Database tables
    │   │   │   │   ├── User.java
    │   │   │   │   ├── Notification.java
    │   │   │   │   ├── NotificationTemplate.java
    │   │   │   │   └── UserPreference.java
    │   │   │   └── enums/                # Fixed value types
    │   │   │       ├── ChannelType.java
    │   │   │       ├── NotificationStatus.java
    │   │   │       └── Priority.java
    │   │   │
    │   │   ├── 📁 repository/            # 🗄️ Database access
    │   │   │   ├── UserRepository.java
    │   │   │   ├── NotificationRepository.java
    │   │   │   ├── NotificationTemplateRepository.java
    │   │   │   └── UserPreferenceRepository.java
    │   │   │
    │   │   ├── 📁 dto/                   # 📦 Data transfer
    │   │   │   ├── request/              # Incoming data
    │   │   │   │   ├── SendNotificationRequest.java
    │   │   │   │   ├── BulkNotificationRequest.java
    │   │   │   │   └── CreateTemplateRequest.java
    │   │   │   └── response/             # Outgoing data
    │   │   │       ├── ApiResponse.java
    │   │   │       ├── NotificationResponse.java
    │   │   │       ├── TemplateResponse.java
    │   │   │       ├── BulkNotificationResponse.java
    │   │   │       └── PagedResponse.java
    │   │   │
    │   │   ├── 📁 service/               # 💼 Business logic
    │   │   │   ├── NotificationService.java
    │   │   │   ├── TemplateService.java
    │   │   │   ├── RateLimiterService.java
    │   │   │   └── channel/              # Channel handlers
    │   │   │       ├── ChannelHandler.java
    │   │   │       ├── ChannelDispatcher.java
    │   │   │       ├── EmailChannelHandler.java
    │   │   │       ├── SmsChannelHandler.java
    │   │   │       ├── PushChannelHandler.java
    │   │   │       └── InAppChannelHandler.java
    │   │   │
    │   │   ├── 📁 controller/            # 🌐 API endpoints
    │   │   │   ├── HealthController.java
    │   │   │   ├── NotificationController.java
    │   │   │   └── TemplateController.java
    │   │   │
    │   │   ├── 📁 kafka/                 # 📬 Message queue
    │   │   │   └── NotificationConsumer.java
    │   │   │
    │   │   ├── 📁 exception/             # ❌ Error handling
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── NotificationException.java
    │   │   │   ├── ResourceNotFoundException.java
    │   │   │   └── RateLimitExceededException.java
    │   │   │
    │   │   └── 📁 scheduler/             # ⏰ Background tasks
    │   │       └── RetryScheduler.java
    │   │
    │   └── resources/
    │       ├── 📄 application.yml        # App configuration
    │       └── db/migration/
    │           ├── V1__init_schema.sql   # Create tables
    │           └── V2__seed_data.sql     # Test data
    │
    └── test/                             # 🧪 Unit tests
        └── java/com/notification/
            └── service/
                └── NotificationServiceTest.java
```

---

## Reading Order (Recommended)

Follow this order to understand the project systematically:

### Phase 1: Understand the Data (30 min)
```
1. model/enums/ChannelType.java       → What channels exist?
2. model/enums/Priority.java          → What priorities exist?
3. model/enums/NotificationStatus.java → What statuses exist?
4. model/entity/User.java             → What is a user?
5. model/entity/NotificationTemplate.java → What is a template?
6. model/entity/Notification.java     → What is a notification?
```

### Phase 2: Understand the API (30 min)
```
7. dto/request/SendNotificationRequest.java → What data comes in?
8. dto/response/NotificationResponse.java   → What data goes out?
9. dto/response/ApiResponse.java            → Standard response format
10. controller/HealthController.java        → Simplest controller
11. controller/NotificationController.java  → Main API endpoints
```

### Phase 3: Understand the Logic (45 min)
```
12. repository/NotificationRepository.java  → How we access DB
13. service/NotificationService.java        → Core business logic
14. service/TemplateService.java            → Template handling
15. service/RateLimiterService.java         → Rate limiting logic
16. service/UserService.java                → User lookups with caching
```

### Phase 4: Understand Advanced Features (30 min)
```
17. service/channel/ChannelHandler.java     → Interface pattern
18. service/channel/EmailChannelHandler.java → Implementation
19. service/channel/ChannelDispatcher.java  → Strategy pattern
20. kafka/NotificationConsumer.java         → Async processing
21. scheduler/RetryScheduler.java           → Retry mechanism
22. controller/UserController.java          → User API with caching
```

### Phase 5: Understand Configuration (15 min)
```
23. config/KafkaConfig.java               → Kafka setup
24. config/RedisConfig.java               → Redis setup
25. resources/application.yml             → App settings
26. exception/GlobalExceptionHandler.java → Error handling
```

---

## Layer-by-Layer Breakdown

---

### 1. Entry Point

#### 📄 `NotificationSystemApplication.java`

**Location:** `src/main/java/com/notification/NotificationSystemApplication.java`

**Purpose:** The starting point of the entire application. Spring Boot looks for this file to boot up.

**What to Notice:**
```java
@SpringBootApplication  // This annotation does 3 things:
                        // 1. @Configuration - This class has config
                        // 2. @EnableAutoConfiguration - Auto-configure based on dependencies
                        // 3. @ComponentScan - Find all @Component, @Service, etc.

public class NotificationSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationSystemApplication.class, args);
        // This single line starts:
        // - Embedded Tomcat server
        // - Database connections
        // - All services, controllers, etc.
    }
}
```

**Key Concept:** This is like the "power button" of the application.

---

### 2. Configuration Layer

Located in: `src/main/java/com/notification/config/`

#### 📄 `KafkaConfig.java`
**Purpose:** Sets up Kafka producer and consumer for async messaging.

**What it configures:**
- Producer: Sends messages to Kafka
- Consumer: Reads messages from Kafka
- Serializers: How to convert objects to/from bytes

#### 📄 `RedisConfig.java`
**Purpose:** Sets up Redis connection for caching and rate limiting.

**What it configures:**
- RedisTemplate: How to interact with Redis
- CacheManager: Spring Cache abstraction with @Cacheable support
- ObjectMapper: JSON serialization with default typing for complex objects
- Connection factory: How to connect to Redis server

**Caching Configuration:**
```java
@Bean
public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    
    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
    
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

**Supported Cache Names:**
- `users` - User lookups (email, phone, device tokens)
- `templates` - Notification templates

#### 📄 `OpenApiConfig.java`
**Purpose:** Sets up Swagger UI for API documentation.

**What it configures:**
- API title, description, version
- Server URL
- Contact information

---

### 3. Model Layer (Entities & Enums)

Located in: `src/main/java/com/notification/model/`

#### Enums (Fixed Value Types)

##### 📄 `ChannelType.java`
```java
public enum ChannelType {
    EMAIL,    // Email notifications
    SMS,      // Text messages
    PUSH,     // Mobile push notifications
    IN_APP    // In-app notifications
}
```
**Purpose:** Limits channel to these 4 values only. Can't accidentally use "TELEGRAM" if it's not defined.

##### 📄 `Priority.java`
```java
public enum Priority {
    LOW,      // Marketing, non-urgent
    MEDIUM,   // Default priority
    HIGH,     // Important alerts
    CRITICAL  // OTP, security alerts
}
```

##### 📄 `NotificationStatus.java`
```java
public enum NotificationStatus {
    PENDING,    // Waiting to be sent
    SENT,       // Sent to provider
    DELIVERED,  // Confirmed delivered
    FAILED,     // Sending failed
    READ        // User has seen it
}
```

#### Entities (Database Tables)

##### 📄 `User.java`
**Maps to:** `users` table

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `email` | String | User's email address |
| `phone` | String | User's phone number |
| `deviceToken` | String | For push notifications |
| `createdAt` | Timestamp | When user was created |

##### 📄 `Notification.java`
**Maps to:** `notifications` table

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `userId` | UUID | Who receives this |
| `channel` | ChannelType | EMAIL, SMS, etc. |
| `priority` | Priority | LOW, MEDIUM, etc. |
| `subject` | String | Notification title |
| `content` | String | Notification body |
| `status` | NotificationStatus | PENDING, SENT, etc. |
| `retryCount` | int | How many retries |
| `createdAt` | Timestamp | When created |
| `sentAt` | Timestamp | When sent |

##### 📄 `NotificationTemplate.java`
**Maps to:** `notification_templates` table

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `name` | String | Template identifier |
| `channel` | ChannelType | Which channel |
| `subjectTemplate` | String | Subject with {{placeholders}} |
| `bodyTemplate` | String | Body with {{placeholders}} |
| `isActive` | boolean | Is template usable |

**Example Template:**
```
Subject: "Welcome, {{userName}}!"
Body: "Hi {{userName}}, thanks for joining on {{date}}."
```

---

### 4. Repository Layer (Database Access)

Located in: `src/main/java/com/notification/repository/`

**What is a Repository?**
A repository is an interface that Spring automatically implements to give you database operations.

#### 📄 `NotificationRepository.java`
```java
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    
    // Spring auto-generates SQL: SELECT * FROM notifications WHERE user_id = ?
    List<Notification> findByUserId(UUID userId);
    
    // Spring auto-generates: SELECT * FROM notifications WHERE status = ?
    List<Notification> findByStatus(NotificationStatus status);
    
    // Custom query for failed notifications that need retry
    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetries")
    List<Notification> findFailedNotificationsForRetry(@Param("maxRetries") int maxRetries);
}
```

**Magic of Spring Data JPA:**
- `findByUserId` → Spring reads the method name and creates the query!
- `findByStatusAndChannel` → Works too!
- No SQL writing needed for basic queries

#### 📄 `UserRepository.java`
```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}
```

#### 📄 `NotificationTemplateRepository.java`
```java
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByNameAndIsActiveTrue(String name);
    List<NotificationTemplate> findByChannelAndIsActiveTrue(ChannelType channel);
}
```

---

### 5. DTO Layer (Data Transfer Objects)

Located in: `src/main/java/com/notification/dto/`

**What is a DTO?**
A DTO is a simple object that carries data between layers. It's different from an Entity:
- **Entity**: Maps directly to database table
- **DTO**: Shapes data for API requests/responses

#### Request DTOs (What comes IN)

##### 📄 `SendNotificationRequest.java`
```java
public class SendNotificationRequest {
    private UUID userId;           // Required: Who to notify
    private ChannelType channel;   // Required: EMAIL, SMS, etc.
    private String templateName;   // Optional: Use a template
    private Map<String, String> templateParams;  // Optional: Template values
    private String subject;        // Optional: Custom subject
    private String content;        // Optional: Custom content
    private Priority priority;     // Optional: Defaults to MEDIUM
}
```

##### 📄 `BulkNotificationRequest.java`
```java
public class BulkNotificationRequest {
    private List<UUID> userIds;    // Multiple users
    // ... same fields as above
}
```

#### Response DTOs (What goes OUT)

##### 📄 `ApiResponse.java`
```java
public class ApiResponse<T> {
    private boolean success;       // true/false
    private String message;        // Human-readable message
    private T data;                // The actual data (generic)
    private LocalDateTime timestamp;
}
```

**Why Generic `<T>`?**
```java
ApiResponse<NotificationResponse>    // For single notification
ApiResponse<List<TemplateResponse>>  // For list of templates
ApiResponse<String>                  // For simple messages
```

##### 📄 `NotificationResponse.java`
```java
public class NotificationResponse {
    private UUID id;
    private UUID userId;
    private ChannelType channel;
    private Priority priority;
    private String subject;
    private String content;
    private NotificationStatus status;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
```

---

### 6. Service Layer (Business Logic)

Located in: `src/main/java/com/notification/service/`

**What is a Service?**
Services contain the business logic - the "brain" of the application.

#### 📄 `NotificationService.java`

**Purpose:** Core notification logic - create, send, retry notifications.

**Key Methods:**

```java
@Service
public class NotificationService {
    
    // Send a single notification
    public NotificationResponse sendNotification(SendNotificationRequest request) {
        // 1. Validate user exists
        // 2. Check rate limit
        // 3. Process template (if used)
        // 4. Create notification entity
        // 5. Save to database
        // 6. Send to Kafka for async processing
        // 7. Return response
    }
    
    // Process notification (called by Kafka consumer)
    public void processNotification(UUID notificationId) {
        // 1. Load notification from DB
        // 2. Dispatch to correct channel handler
        // 3. Update status (SENT or FAILED)
    }
    
    // Get user's notifications
    public PagedResponse<NotificationResponse> getUserNotifications(
        UUID userId, int page, int size, NotificationStatus status) {
        // 1. Query database with pagination
        // 2. Convert entities to DTOs
        // 3. Return paged response
    }
}
```

#### 📄 `TemplateService.java`

**Purpose:** Manage notification templates and render them.

**Key Methods:**
```java
@Service
public class TemplateService {
    
    // Render a template with parameters
    public String renderTemplate(String template, Map<String, String> params) {
        // "Hello {{userName}}" + {userName: "John"} = "Hello John"
    }
    
    // Get all templates
    public List<TemplateResponse> getAllTemplates();
    
    // Create new template
    public TemplateResponse createTemplate(CreateTemplateRequest request);
}
```

#### 📄 `RateLimiterService.java`

**Purpose:** Prevent users from sending too many notifications.

**How it Works:**
```java
@Service
public class RateLimiterService {
    
    // Check and consume rate limit
    public boolean tryConsume(UUID userId) {
        String key = "rate_limit:" + userId;
        Long count = redis.increment(key);
        
        if (count == 1) {
            redis.expire(key, 60, SECONDS);  // Reset after 1 minute
        }
        
        return count <= 10;  // Allow 10 per minute
    }
}
```

#### 📄 `UserService.java`

**Purpose:** User management with Redis caching for performance.

**Caching Implementation:**
```java
@Service
public class UserService {
    
    // Cache user lookup by email
    @Cacheable(value = "users", key = "'email:' + #email")
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
    
    // Cache user lookup by phone
    @Cacheable(value = "users", key = "'phone:' + #phone")
    public User findByPhone(String phone) {
        return userRepository.findByPhone(phone)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
    
    // Cache list of users with device tokens
    @Cacheable(value = "users", key = "'deviceTokens'")
    public List<User> findUsersWithDeviceTokens() {
        return userRepository.findByDeviceTokenIsNotNull();
    }
}
```

**Cache Eviction:**
```java
// Evict cache when user data changes
@CacheEvict(value = "users", key = "'email:' + #oldEmail")
public void evictUserCacheByEmail(String oldEmail) {
    // Cache entry removed
}
```

#### Channel Handlers

Located in: `src/main/java/com/notification/service/channel/`

##### 📄 `ChannelHandler.java` (Interface)
```java
public interface ChannelHandler {
    void send(Notification notification, User user);
    ChannelType getChannelType();
}
```

##### 📄 `EmailChannelHandler.java`
```java
@Component
public class EmailChannelHandler implements ChannelHandler {
    
    @Override
    public void send(Notification notification, User user) {
        // In real app: Use JavaMail or SendGrid
        // Here: Just log it
        log.info("Sending EMAIL to {}: {}", user.getEmail(), notification.getSubject());
    }
    
    @Override
    public ChannelType getChannelType() {
        return ChannelType.EMAIL;
    }
}
```

##### 📄 `ChannelDispatcher.java`
```java
@Component
public class ChannelDispatcher {
    
    private Map<ChannelType, ChannelHandler> handlers;
    
    // Automatically collects all ChannelHandler implementations
    public ChannelDispatcher(List<ChannelHandler> handlerList) {
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(
                ChannelHandler::getChannelType,
                handler -> handler
            ));
    }
    
    // Route to correct handler
    public void dispatch(Notification notification, User user) {
        ChannelHandler handler = handlers.get(notification.getChannel());
        handler.send(notification, user);
    }
}
```

---

### 7. Controller Layer (API Endpoints)

Located in: `src/main/java/com/notification/controller/`

**What is a Controller?**
Controllers handle HTTP requests and return HTTP responses. They're the "front door" of your API.

#### 📄 `HealthController.java`

**Purpose:** Simple endpoint to check if API is running.

```java
@RestController
@RequestMapping("/api/v1")
public class HealthController {
    
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(
            ApiResponse.success("OK", "Service is healthy")
        );
    }
}
```

**URL:** `GET /api/v1/health`

#### 📄 `NotificationController.java`

**Purpose:** All notification-related endpoints.

```java
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    
    // POST /api/v1/notifications
    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> sendNotification(
            @RequestBody SendNotificationRequest request) {
        NotificationResponse response = notificationService.sendNotification(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Notification queued"));
    }
    
    // POST /api/v1/notifications/bulk
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<BulkNotificationResponse>> sendBulk(
            @RequestBody BulkNotificationRequest request) {
        // ...
    }
    
    // GET /api/v1/notifications/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotification(
            @PathVariable UUID id) {
        // ...
    }
    
    // GET /api/v1/notifications/user/{userId}
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getUserNotifications(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // ...
    }
}
```

#### 📄 `TemplateController.java`

**Purpose:** CRUD operations for notification templates.

| Method | URL | Purpose |
|--------|-----|---------|
| GET | `/api/v1/templates` | List all templates |
| GET | `/api/v1/templates/{id}` | Get template by ID |
| GET | `/api/v1/templates/name/{name}` | Get template by name |
| POST | `/api/v1/templates` | Create new template |
| PUT | `/api/v1/templates/{id}` | Update template |
| DELETE | `/api/v1/templates/{id}` | Delete template |

#### 📄 `UserController.java`

**Purpose:** User lookup endpoints with Redis caching for testing.

**Caching Endpoints:**
```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    // GET /api/v1/users/email/{email}
    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<User>> getUserByEmail(@PathVariable String email) {
        User user = userService.findByEmail(email);  // @Cacheable
        return ResponseEntity.ok(ApiResponse.success("User found", user));
    }
    
    // GET /api/v1/users/phone/{phone}
    @GetMapping("/phone/{phone}")
    public ResponseEntity<ApiResponse<User>> getUserByPhone(@PathVariable String phone) {
        User user = userService.findByPhone(phone);  // @Cacheable
        return ResponseEntity.ok(ApiResponse.success("User found", user));
    }
    
    // GET /api/v1/users/push-eligible
    @GetMapping("/push-eligible")
    public ResponseEntity<ApiResponse<List<User>>> getPushEligibleUsers() {
        List<User> users = userService.findUsersWithDeviceTokens();  // @Cacheable
        return ResponseEntity.ok(ApiResponse.success("Push-eligible users retrieved", users));
    }
}
```

**Cache Testing:**
- First request: Database hit, result cached
- Subsequent requests: Cache hit, no database query
- Failed lookups: Exception thrown, not cached

---

### 8. Kafka Layer (Message Queue)

Located in: `src/main/java/com/notification/kafka/`

#### 📄 `NotificationConsumer.java`

**Purpose:** Listens to channel-specific Kafka topics and processes notifications asynchronously.

**Channel-Specific Topics (Alex Xu's Design Pattern):**
- `notifications.email` - Email notifications
- `notifications.sms` - SMS notifications  
- `notifications.push` - Push notifications
- `notifications.in-app` - In-app notifications

```java
@Component
public class NotificationConsumer {
    
    // Each channel has its own listener for independent scaling
    @KafkaListener(topics = "${notification.kafka.topic.email:notifications.email}",
                   groupId = "${spring.kafka.consumer.group-id:notification-service}-email")
    public void processEmailNotification(ConsumerRecord<String, String> record, 
                                         Acknowledgment acknowledgment) {
        processNotification(record, acknowledgment, "EMAIL");
    }
    
    @KafkaListener(topics = "${notification.kafka.topic.sms:notifications.sms}",
                   groupId = "${spring.kafka.consumer.group-id:notification-service}-sms")
    public void processSmsNotification(ConsumerRecord<String, String> record,
                                       Acknowledgment acknowledgment) {
        processNotification(record, acknowledgment, "SMS");
    }
    
    // ... similar for PUSH and IN_APP
    
    private void processNotification(ConsumerRecord<String, String> record,
                                     Acknowledgment acknowledgment, String channel) {
        UUID notificationId = UUID.fromString(record.value());
        // Fetch from DB, dispatch to handler, update status
    }
}
```

**How Async Processing Works:**

```
┌──────────┐    ┌─────────┐    ┌────────────────────────┐    ┌────────────────┐
│  Client  │───>│   API   │───>│  Channel-Specific      │───>│    Consumer    │
│          │    │         │    │  Kafka Topics          │    │    (Worker)    │
└──────────┘    └─────────┘    │  ├─ notifications.email│    └────────────────┘
                    │          │  ├─ notifications.sms  │           │
                    │          │  ├─ notifications.push │           │
                    │          │  └─ notifications.in-app│          │
                    │          └────────────────────────┘           │
                    │ Returns immediately                           │ Processes async
                    │ with "PENDING" status                         │ Updates to "SENT"
                    ▼                                               ▼
```

**Why Channel-Specific Topics?**
1. **Independent Scaling**: More email consumers, fewer SMS consumers based on volume
2. **Fault Isolation**: Email provider issues don't affect push notifications
3. **Different SLAs**: Push can have higher processing priority
4. **Better Monitoring**: Track lag and throughput per channel separately

---

### 9. Exception Handling

Located in: `src/main/java/com/notification/exception/`

#### Custom Exceptions

##### 📄 `ResourceNotFoundException.java`
```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: %s", resource, field, value));
    }
}

// Usage: throw new ResourceNotFoundException("User", "id", userId);
// Message: "User not found with id: 123e4567-..."
```

##### 📄 `RateLimitExceededException.java`
```java
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
```

#### 📄 `GlobalExceptionHandler.java`

**Purpose:** Catches all exceptions and returns proper API responses.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ApiResponse.error(ex.getMessage()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred"));
    }
}
```

---

### 10. Scheduler Layer

Located in: `src/main/java/com/notification/scheduler/`

#### 📄 `RetryScheduler.java`

**Purpose:** Periodically retry failed notifications.

```java
@Component
public class RetryScheduler {
    
    @Scheduled(fixedRate = 60000)  // Run every 60 seconds
    public void retryFailedNotifications() {
        // 1. Find failed notifications with retryCount < 3
        List<Notification> failed = notificationRepository
            .findFailedNotificationsForRetry(3);
        
        // 2. Re-send each one
        for (Notification notification : failed) {
            notification.setRetryCount(notification.getRetryCount() + 1);
            notificationService.processNotification(notification.getId());
        }
    }
}
```

---

## Request Flow Diagram

### Complete Flow: Send Notification

```
┌────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT REQUEST                                    │
│  POST /api/v1/notifications                                                │
│  {"userId": "...", "channel": "EMAIL", "templateName": "welcome-email"}   │
└────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                         CONTROLLER LAYER                                    │
│  NotificationController.sendNotification()                                 │
│  - Receives HTTP request                                                   │
│  - Validates request body                                                  │
│  - Calls service layer                                                     │
└────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                          SERVICE LAYER                                      │
│  NotificationService.sendNotification()                                    │
│                                                                            │
│  Step 1: Validate user exists                                              │
│          └─> UserRepository.findById(userId)                               │
│                                                                            │
│  Step 2: Check rate limit                                                  │
│          └─> RateLimiterService.tryConsume(userId)                         │
│          └─> Redis: INCR rate_limit:userId                                 │
│                                                                            │
│  Step 3: Get template if specified                                         │
│          └─> TemplateRepository.findByName("welcome-email")                │
│          └─> TemplateService.renderTemplate(template, params)              │
│                                                                            │
│  Step 4: Create notification entity                                        │
│          └─> new Notification(..., status=PENDING)                         │
│                                                                            │
│  Step 5: Save to database                                                  │
│          └─> NotificationRepository.save(notification)                     │
│          └─> PostgreSQL: INSERT INTO notifications...                      │
│                                                                            │
│  Step 6: Send to channel-specific Kafka topic (Alex Xu's pattern)          │
│          └─> KafkaTemplate.send("notifications.email", notificationId)     │
│          └─> Or: notifications.sms, notifications.push, notifications.in-app│
│                                                                            │
│  Step 7: Return response with status=PENDING                               │
└────────────────────────────────────────────────────────────────────────────┘
                                      │
                    ┌─────────────────┴─────────────────┐
                    │                                   │
                    ▼                                   ▼
┌──────────────────────────────────┐    ┌──────────────────────────────────┐
│      IMMEDIATE RESPONSE          │    │      ASYNC PROCESSING            │
│                                  │    │      (Channel-Specific Consumer) │
│  {                               │    │                                  │
│    "success": true,              │    │  NotificationConsumer            │
│    "data": {                     │    │  .processEmailNotification()     │
│      "id": "abc-123",            │    │  .processSmsNotification()       │
│      "status": "PENDING"         │    │  .processPushNotification()      │
│    }                             │    │  .processInAppNotification()     │
│  }                               │    │                                  │
│                                  │    │  Step 1: Parse notification ID   │
└──────────────────────────────────┘    │                                  │
                                        │  Step 2: Load from database      │
                                        │                                  │
                                        │  Step 3: Get user details        │
                                        │                                  │
                                        │  Step 4: Dispatch to channel     │
                                        │          └─> ChannelDispatcher   │
                                        │          └─> EmailChannelHandler │
                                        │                                  │
                                        │  Step 5: Update status to SENT   │
                                        │          └─> PostgreSQL UPDATE   │
                                        └──────────────────────────────────┘
```

---

## Database Schema

### Visual Schema

```
┌─────────────────────┐       ┌─────────────────────────────┐
│       users         │       │    notification_templates    │
├─────────────────────┤       ├─────────────────────────────┤
│ id (PK, UUID)       │       │ id (PK, UUID)               │
│ email               │       │ name (unique)               │
│ phone               │       │ channel (enum)              │
│ device_token        │       │ subject_template            │
│ created_at          │       │ body_template               │
│ updated_at          │       │ is_active                   │
└─────────────────────┘       │ created_at                  │
         │                    │ updated_at                  │
         │                    └─────────────────────────────┘
         │
         │ 1:N
         ▼
┌─────────────────────────────┐
│       notifications         │
├─────────────────────────────┤
│ id (PK, UUID)               │
│ user_id (FK → users)        │
│ channel (enum)              │
│ priority (enum)             │
│ subject                     │
│ content                     │
│ status (enum)               │
│ retry_count                 │
│ metadata (JSON)             │
│ created_at                  │
│ sent_at                     │
│ delivered_at                │
│ read_at                     │
└─────────────────────────────┘

┌─────────────────────────────┐
│     user_preferences        │
├─────────────────────────────┤
│ id (PK, UUID)               │
│ user_id (FK → users)        │
│ channel (enum)              │
│ enabled                     │
│ quiet_hours_start           │
│ quiet_hours_end             │
│ created_at                  │
│ updated_at                  │
└─────────────────────────────┘
```

---

## Configuration Files

### 📄 `application.yml`

```yaml
# Server configuration
server:
  port: 8080                    # API runs on port 8080

# Database configuration
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/notification_db
    username: postgres
    password: postgres
  
  # JPA/Hibernate settings
  jpa:
    hibernate:
      ddl-auto: validate        # Don't auto-create tables (Flyway does it)
    show-sql: true              # Log all SQL queries
  
  # Redis settings
  redis:
    host: localhost
    port: 6379
  
  # Kafka settings
  kafka:
    bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
    consumer:
      group-id: notification-group
      auto-offset-reset: earliest

# Custom application settings
notification:
  # Channel-specific Kafka topics (Alex Xu's design pattern)
  kafka:
    topic:
      email: notifications.email       # Email notifications
      sms: notifications.sms           # SMS notifications
      push: notifications.push         # Push notifications
      in-app: notifications.in-app     # In-app notifications
      dlq: notifications.dlq           # Dead Letter Queue
  rate-limit:
    requests-per-minute: 10     # Max 10 notifications per user per minute
  retry:
    max-attempts: 3             # Retry failed notifications 3 times
```

### 📄 `pom.xml` Key Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | REST API support |
| `spring-boot-starter-data-jpa` | Database ORM |
| `spring-boot-starter-data-redis` | Redis integration |
| `spring-kafka` | Kafka integration |
| `postgresql` | PostgreSQL driver |
| `flyway-core` | Database migrations |
| `springdoc-openapi` | Swagger UI |
| `lombok` | Reduce boilerplate code |

---

## Key Design Patterns Used

### 1. Repository Pattern
**Where:** `repository/` package
**What:** Abstracts database access behind interfaces
**Why:** Makes it easy to swap databases or add caching

### 2. Service Layer Pattern
**Where:** `service/` package
**What:** All business logic in dedicated service classes
**Why:** Controllers stay thin, logic is reusable

### 3. DTO Pattern
**Where:** `dto/` package
**What:** Separate objects for API input/output
**Why:** Don't expose internal entity structure

### 4. Strategy Pattern
**Where:** `service/channel/`
**What:** `ChannelHandler` interface with multiple implementations
**Why:** Easy to add new channels without changing existing code

### 5. Factory Pattern
**Where:** `ChannelDispatcher`
**What:** Creates/selects correct handler based on channel type
**Why:** Centralizes handler selection logic

### 6. Global Exception Handler Pattern
**Where:** `exception/GlobalExceptionHandler.java`
**What:** Single place to handle all exceptions
**Why:** Consistent error responses across entire API

---

## How Components Connect

### Dependency Injection Flow

```
Spring Container
      │
      ├── Creates NotificationController
      │       │
      │       └── Injects NotificationService
      │               │
      │               ├── Injects NotificationRepository
      │               ├── Injects UserRepository
      │               ├── Injects TemplateService
      │               ├── Injects RateLimiterService
      │               ├── Injects ChannelDispatcher
      │               └── Injects KafkaTemplate
      │
      ├── Creates ChannelDispatcher
      │       │
      │       └── Injects List<ChannelHandler>
      │               │
      │               ├── EmailChannelHandler
      │               ├── SmsChannelHandler
      │               ├── PushChannelHandler
      │               └── InAppChannelHandler
      │
      └── Creates NotificationConsumer
              │
              └── Injects NotificationService
```

---

## Quick Reference

### File → Purpose Mapping

| File | One-Line Purpose |
|------|------------------|
| `NotificationSystemApplication.java` | Starts the app |
| `KafkaConfig.java` | Configures Kafka |
| `RedisConfig.java` | Configures Redis |
| `User.java` | User database table |
| `Notification.java` | Notification database table |
| `ChannelType.java` | EMAIL, SMS, PUSH, IN_APP |
| `NotificationStatus.java` | PENDING, SENT, FAILED, etc. |
| `NotificationRepository.java` | Database queries for notifications |
| `SendNotificationRequest.java` | API request body structure |
| `NotificationResponse.java` | API response structure |
| `NotificationService.java` | Core notification logic |
| `TemplateService.java` | Template rendering |
| `RateLimiterService.java` | Rate limiting with Redis |
| `ChannelHandler.java` | Interface for channel handlers |
| `EmailChannelHandler.java` | Sends emails |
| `ChannelDispatcher.java` | Routes to correct handler |
| `NotificationController.java` | Notification API endpoints |
| `NotificationConsumer.java` | Kafka message consumer |
| `RetryScheduler.java` | Retries failed notifications |
| `GlobalExceptionHandler.java` | Error handling |

### Annotation Cheat Sheet

| Annotation | Meaning |
|------------|---------|
| `@SpringBootApplication` | Main application class |
| `@RestController` | This class handles HTTP requests |
| `@Service` | This class contains business logic |
| `@Repository` | This class accesses database |
| `@Component` | Generic Spring-managed class |
| `@Autowired` | Inject a dependency |
| `@GetMapping` | Handle GET requests |
| `@PostMapping` | Handle POST requests |
| `@RequestBody` | Parse JSON body |
| `@PathVariable` | Extract from URL path |
| `@RequestParam` | Extract from query params |
| `@Entity` | This class maps to a table |
| `@Table` | Specify table name |
| `@Id` | Primary key field |
| `@Column` | Specify column name |
| `@KafkaListener` | Listen to Kafka topic |
| `@Scheduled` | Run periodically |

---

## Next Steps

After reading through this guide:

1. **Run the Application** - See it in action
2. **Test the APIs** - Use the API Testing Guide
3. **Read the Code** - Follow the recommended reading order
4. **Make Changes** - Try adding a new channel or template
5. **Write Tests** - Check the test files for examples

---

*Happy Coding! 🚀*
