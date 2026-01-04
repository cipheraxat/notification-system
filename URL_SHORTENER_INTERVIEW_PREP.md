# 📋 URL Shortener - Interview Preparation Guide

> A comprehensive guide to explain your URL Shortener project and answer SDE-2 interview questions.

---

## Table of Contents

1. [60-Second Elevator Pitch](#60-second-elevator-pitch)
2. [Project Architecture Overview](#project-architecture-overview)
3. [Component Deep Dives](#component-deep-dives)
4. [Design Decisions & Trade-offs](#design-decisions--trade-offs)
5. [Interview Questions & Answers](#interview-questions--answers)
   - [System Design Questions](#system-design-questions)
   - [Short Code Generation Questions](#short-code-generation-questions)
   - [Database & Data Modeling Questions](#database--data-modeling-questions)
   - [Security & Authentication Questions](#security--authentication-questions)
   - [API Design Questions](#api-design-questions)
   - [Scaling & Performance Questions](#scaling--performance-questions)
   - [Edge Cases & Error Handling](#edge-cases--error-handling)
6. [How to Whiteboard This System](#how-to-whiteboard-this-system)
7. [Keywords to Use in Interviews](#keywords-to-use-in-interviews)

---

## 60-Second Elevator Pitch

> **Use this when asked: "Tell me about a project you've worked on"**

*"I built a URL shortening service like Bitly using Spring Boot and PostgreSQL.*

*Key features include:*
- *Short code generation with collision handling*
- *HTTP 302 redirects with click tracking and analytics*
- *Link expiration and private URL access control*
- *User authentication with Spring Security and BCrypt*
- *User dashboard for managing URLs and an admin panel for analytics*

*The flow is simple: user submits a long URL → I generate a unique 6-character code → store it in PostgreSQL → return the short URL. When someone visits the short URL, I look it up, increment the click count, and redirect to the original."*

---

## Project Architecture Overview

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            URL SHORTENER SYSTEM                              │
└─────────────────────────────────────────────────────────────────────────────┘

                         CREATE SHORT URL
                         ================
┌──────────┐     ┌─────────────────────────────────────────┐     ┌────────────┐
│  Client  │────▶│  POST /api/urls                         │────▶│ PostgreSQL │
│          │     │  { "originalUrl": "https://long..." }   │     │            │
└──────────┘     └─────────────────────────────────────────┘     └────────────┘
                              │
                              ▼
                 ┌─────────────────────────────┐
                 │  1. Validate URL            │
                 │  2. Generate 6-char code    │
                 │  3. Check for collision     │
                 │  4. Save to database        │
                 │  5. Return short URL        │
                 └─────────────────────────────┘
                              │
                              ▼
                 { "shortUrl": "http://short.ly/abc123" }


                         REDIRECT FLOW
                         =============
┌──────────┐     ┌─────────────────────────────────────────┐     ┌────────────┐
│  Browser │────▶│  GET /abc123                            │────▶│ PostgreSQL │
│          │     │  (Short code in path)                   │     │  (Lookup)  │
└──────────┘     └─────────────────────────────────────────┘     └────────────┘
                              │
                              ▼
                 ┌─────────────────────────────┐
                 │  1. Look up short code      │
                 │  2. Check if expired        │
                 │  3. Increment click count   │
                 │  4. Return 302 Redirect     │
                 └─────────────────────────────┘
                              │
                              ▼
                 HTTP 302 → Location: https://original-long-url.com
```

### Request Lifecycle

| Step | Action |
|------|--------|
| 1 | User submits long URL via POST request |
| 2 | Validate URL format (must be valid HTTP/HTTPS) |
| 3 | Generate random 6-character alphanumeric code |
| 4 | Check if code already exists in database |
| 5 | If collision, regenerate; else save |
| 6 | Return short URL to user |
| 7 | On redirect request, lookup by short code |
| 8 | If found and not expired, increment clicks and redirect |
| 9 | If expired, return 410 Gone |
| 10 | If not found, return 404 Not Found |

---

## Component Deep Dives

### 1. Short Code Generation

**Algorithm:**
```
Character Set: a-z, A-Z, 0-9 (62 characters)
Code Length: 6 characters
Total Combinations: 62^6 = 56.8 billion unique codes
```

**How it works:**
```java
private static final String CHARACTERS = 
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

public String generateShortCode() {
    SecureRandom random = new SecureRandom();
    StringBuilder code = new StringBuilder(6);
    
    for (int i = 0; i < 6; i++) {
        int index = random.nextInt(CHARACTERS.length());
        code.append(CHARACTERS.charAt(index));
    }
    
    return code.toString();
}
```

**Collision Handling:**
```java
public String generateUniqueCode() {
    String code;
    int attempts = 0;
    
    do {
        code = generateShortCode();
        attempts++;
        if (attempts > 10) {
            throw new RuntimeException("Unable to generate unique code");
        }
    } while (urlRepository.existsByShortCode(code));
    
    return code;
}
```

**Why SecureRandom?**
- Cryptographically secure random numbers
- Unpredictable codes (can't guess next URL)
- Better than `Math.random()` for security

---

### 2. HTTP Redirect Flow

```
┌──────────┐                    ┌──────────────┐                    ┌──────────┐
│  Browser │                    │   Server     │                    │  Target  │
└────┬─────┘                    └──────┬───────┘                    └────┬─────┘
     │                                 │                                  │
     │  GET /abc123                    │                                  │
     │────────────────────────────────▶│                                  │
     │                                 │                                  │
     │                                 │ 1. Lookup "abc123"               │
     │                                 │ 2. Found: https://example.com    │
     │                                 │ 3. Increment click_count         │
     │                                 │                                  │
     │  HTTP 302 Found                 │                                  │
     │  Location: https://example.com  │                                  │
     │◀────────────────────────────────│                                  │
     │                                 │                                  │
     │  GET https://example.com        │                                  │
     │────────────────────────────────────────────────────────────────────▶│
     │                                 │                                  │
```

**Why HTTP 302 (not 301)?**

| Status | Meaning | Caching | Use Case |
|--------|---------|---------|----------|
| **301** | Moved Permanently | Browser caches forever | Permanent redirects |
| **302** | Found (Temporary) | No caching | **Our choice** - allows click tracking |

If we used 301, browsers would cache the redirect and skip our server. We'd lose click tracking!

---

### 3. Database Schema

```sql
-- Main URL table
CREATE TABLE urls (
    id              BIGSERIAL PRIMARY KEY,
    short_code      VARCHAR(10) UNIQUE NOT NULL,
    original_url    TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP,
    click_count     BIGINT DEFAULT 0,
    is_private      BOOLEAN DEFAULT FALSE,
    user_id         BIGINT REFERENCES users(id),
    
    -- Index for fast lookups
    CONSTRAINT idx_short_code UNIQUE (short_code)
);

-- Index for redirect lookups (most frequent query)
CREATE INDEX idx_urls_short_code ON urls(short_code);

-- Index for user's URLs
CREATE INDEX idx_urls_user_id ON urls(user_id);

-- Users table
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20) DEFAULT 'USER',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Why these indexes?**
- `short_code` index: O(log n) lookup for redirects (most critical query)
- `user_id` index: Fast listing of user's URLs for dashboard

---

### 4. Authentication (Spring Security)

**Password Hashing with BCrypt:**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// Registration
String hashedPassword = passwordEncoder.encode(rawPassword);
user.setPassword(hashedPassword);

// Login verification
boolean matches = passwordEncoder.matches(rawPassword, hashedPassword);
```

**Why BCrypt?**
- Slow by design (prevents brute force)
- Built-in salt (each hash is unique)
- Configurable work factor

**Role-Based Access:**
| Role | Can Do |
|------|--------|
| USER | Create URLs, manage own URLs, view own analytics |
| ADMIN | Everything + view all users, system analytics |

---

## Design Decisions & Trade-offs

| Decision | Why | Trade-off |
|----------|-----|-----------|
| **Random codes (not sequential)** | Security - can't guess URLs | Possible collisions (handled) |
| **6 characters** | 56B combinations, short URLs | Longer = more combinations |
| **PostgreSQL** | ACID, reliable, good indexes | Harder to scale horizontally |
| **HTTP 302 (not 301)** | Enables click tracking | Slightly slower (no browser cache) |
| **Server-side expiration check** | Simple, no background jobs | Expired rows stay in DB |
| **BCrypt for passwords** | Industry standard, secure | Slower than SHA (intentional) |

---

## Interview Questions & Answers

### System Design Questions

---

**Q1: Walk me through how URL shortening works in your system.**

**Answer:**
"When a user wants to shorten a URL:

1. They send a POST request with the original URL
2. I validate it's a proper HTTP/HTTPS URL
3. I generate a random 6-character code using alphanumeric characters
4. I check if this code already exists in the database
5. If it exists (collision), I generate a new one
6. I save the mapping to PostgreSQL
7. I return the short URL to the user

For redirection:
1. User visits the short URL
2. I extract the code from the path
3. I look it up in the database
4. If found and not expired, I increment the click count
5. I return HTTP 302 with the Location header set to the original URL
6. The browser automatically follows the redirect"

---

**Q2: Why did you choose random generation over sequential IDs?**

**Answer:**
"I chose random for two main reasons:

1. **Security:** Sequential IDs are guessable. If my URL is `/abc123`, an attacker could try `/abc122` and `/abc124` to find other URLs. Random codes are unpredictable.

2. **Privacy:** Sequential IDs reveal how many URLs have been created. With random codes, you can't tell if there are 100 or 1 million URLs.

The trade-off is handling collisions, but with 56 billion possible combinations and a simple retry loop, collisions are rare."

---

**Q3: How do you handle collisions?**

**Answer:**
"I use a simple retry approach:

1. Generate a random 6-character code
2. Check if it exists in the database
3. If yes, generate a new code and repeat
4. If no, use this code

With 62^6 = 56 billion combinations, the probability of collision is very low. Even with 1 million URLs, the chance of collision is about 0.002%.

I also set a max retry limit (10 attempts) to prevent infinite loops. If we hit that, something is seriously wrong with the system."

---

**Q4: Why HTTP 302 instead of 301?**

**Answer:**
"The key difference:

- **301 (Moved Permanently):** Browsers cache this forever. Next time, they go directly to the destination without hitting our server.

- **302 (Found/Temporary):** Browsers don't cache. Every request comes to our server first.

I chose 302 because:
1. **Click tracking:** I can count every click
2. **Flexibility:** I can change the destination URL later
3. **Expiration:** I can check if the link has expired

If I used 301, browsers would bypass our server and we'd lose all analytics."

---

### Short Code Generation Questions

---

**Q5: Why 6 characters? How many URLs can you support?**

**Answer:**
"With 62 characters (a-z, A-Z, 0-9) and 6 positions:

62^6 = 56.8 billion unique combinations

This is more than enough for most use cases. For reference, Bitly processes about 600 million links per month.

If we ever needed more:
- 7 characters = 3.5 trillion combinations
- 8 characters = 218 trillion combinations

I started with 6 for shorter, more user-friendly URLs."

---

**Q6: Why SecureRandom instead of Math.random()?**

**Answer:**
"`Math.random()` is predictable. If an attacker knows the seed, they can predict future values.

`SecureRandom` uses cryptographically secure random number generation. It's:
- Unpredictable
- Non-reproducible
- Suitable for security-sensitive applications

For a URL shortener, this means attackers can't guess other users' short codes."

---

**Q7: What if two users try to shorten the same URL?**

**Answer:**
"In my current design, they each get a different short code. This is intentional because:

1. **Privacy:** User A shouldn't know User B shortened the same URL
2. **Analytics:** Each user wants their own click count
3. **Expiration:** Each user might set different expiration times

If I wanted to deduplicate (save storage), I could:
- Hash the original URL
- Check if that hash exists
- Return the same short code

But that creates privacy concerns, so I kept it simple - one short code per request."

---

### Database & Data Modeling Questions

---

**Q8: How do you ensure fast lookups for redirects?**

**Answer:**
"Redirects are the most frequent operation, so they must be fast:

1. **Index on short_code:** 
   ```sql
   CREATE INDEX idx_urls_short_code ON urls(short_code);
   ```
   This gives O(log n) lookup time using a B-tree index.

2. **Query optimization:**
   ```sql
   SELECT original_url, expires_at, click_count 
   FROM urls 
   WHERE short_code = 'abc123';
   ```
   Only fetches needed columns.

3. **Connection pooling:** Spring Boot's HikariCP reuses database connections.

With proper indexing, lookups take ~1-2ms even with millions of rows."

---

**Q9: How do you handle click count updates without slowing redirects?**

**Answer:**
"I update the click count inline with the redirect:

```java
@Transactional
public String redirect(String shortCode) {
    Url url = urlRepository.findByShortCode(shortCode)
        .orElseThrow(() -> new NotFoundException("URL not found"));
    
    url.incrementClickCount();
    urlRepository.save(url);
    
    return url.getOriginalUrl();
}
```

For higher scale, I would:
1. **Async updates:** Put click events in a queue, update in batches
2. **Caching:** Cache hot URLs in Redis, update DB periodically
3. **Eventual consistency:** Accept slight delays in click count accuracy

But for current scale, synchronous updates work fine."

---

**Q10: How do you handle link expiration?**

**Answer:**
"I store an `expires_at` timestamp with each URL. During redirect:

```java
public String redirect(String shortCode) {
    Url url = urlRepository.findByShortCode(shortCode)
        .orElseThrow(() -> new NotFoundException("URL not found"));
    
    if (url.isExpired()) {
        throw new GoneException("This link has expired");
    }
    
    url.incrementClickCount();
    return url.getOriginalUrl();
}

// In the entity
public boolean isExpired() {
    return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
}
```

I return HTTP 410 (Gone) for expired links instead of 404, because the URL did exist but is no longer valid."

---

### Security & Authentication Questions

---

**Q11: How do you store passwords?**

**Answer:**
"I use BCrypt hashing:

1. **Never store plain text** - Only the hash is stored
2. **BCrypt includes salt** - Each password has a unique salt, so identical passwords have different hashes
3. **Work factor** - BCrypt is intentionally slow (configurable rounds), making brute force impractical

```java
// During registration
String hash = passwordEncoder.encode(rawPassword);
user.setPasswordHash(hash);

// During login
boolean valid = passwordEncoder.matches(inputPassword, storedHash);
```

Even if the database is compromised, passwords are not exposed."

---

**Q12: How does authentication work?**

**Answer:**
"I use Spring Security with session-based authentication:

1. **Login:** User submits email + password
2. **Verification:** Spring Security checks against BCrypt hash
3. **Session:** On success, create a session and return session cookie
4. **Subsequent requests:** Browser sends cookie, server validates session

For REST APIs, I could also use:
- JWT tokens (stateless)
- OAuth2 for social login

Session-based is simpler for a monolithic app with a web UI."

---

**Q13: How do private URLs work?**

**Answer:**
"Private URLs can only be accessed by the owner:

```java
public String redirect(String shortCode, User currentUser) {
    Url url = urlRepository.findByShortCode(shortCode)
        .orElseThrow(() -> new NotFoundException("URL not found"));
    
    if (url.isPrivate() && !url.getOwner().equals(currentUser)) {
        throw new ForbiddenException("This is a private URL");
    }
    
    return url.getOriginalUrl();
}
```

Use cases:
- Personal bookmarks
- Internal company links
- Links that shouldn't be shared"

---

### API Design Questions

---

**Q14: What are your main API endpoints?**

**Answer:**
```
POST   /api/urls              - Create short URL
GET    /api/urls              - List user's URLs (paginated)
GET    /api/urls/{id}         - Get URL details
DELETE /api/urls/{id}         - Delete URL
GET    /api/urls/{id}/stats   - Get click analytics

GET    /{shortCode}           - Redirect to original URL

POST   /api/auth/register     - User registration
POST   /api/auth/login        - User login
POST   /api/auth/logout       - User logout
```

I separated the API (`/api/*`) from the redirect endpoint (`/{shortCode}`) for clarity."

---

**Q15: How do you handle pagination for the user dashboard?**

**Answer:**
"I use Spring Data's Pageable:

```java
@GetMapping("/api/urls")
public Page<UrlDto> getUserUrls(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @AuthenticationPrincipal User user) {
    
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    return urlRepository.findByUserId(user.getId(), pageable);
}
```

Response includes:
- `content`: List of URLs for current page
- `totalElements`: Total URL count
- `totalPages`: Total pages available
- `number`: Current page number

This prevents loading thousands of URLs at once."

---

### Scaling & Performance Questions

---

**Q16: How would you scale this to handle millions of requests per day?**

**Answer:**
"I would scale in layers:

1. **Caching (Redis):**
   - Cache popular short codes
   - 80% of traffic goes to 20% of URLs
   - Cache hit = skip database entirely

2. **Read replicas:**
   - Redirects are reads, creation is writes
   - Route reads to replicas

3. **Load balancer:**
   - Multiple app instances
   - Round-robin or least-connections

4. **CDN:**
   - Cache redirects at edge locations
   - Faster response for global users

5. **Database optimization:**
   - Partition by creation date
   - Archive old URLs

For most use cases, caching alone handles 10x traffic."

---

**Q17: What's the bottleneck in your current design?**

**Answer:**
"The main bottleneck is the database for redirects:

Every redirect = 1 SELECT + 1 UPDATE (for click count)

Solutions:
1. **Cache hot URLs:** Most clicks go to few URLs. Cache them in Redis.

2. **Batch click updates:** Don't update on every click. Increment in Redis, flush to DB every minute.

3. **Connection pooling:** Already using HikariCP, but tune pool size.

4. **Read replicas:** Separate read and write traffic.

For current scale, PostgreSQL with proper indexes handles thousands of requests per second."

---

**Q18: How would you handle a viral link with millions of clicks?**

**Answer:**
"A viral link could overwhelm the database with click count updates.

Solution: **Rate-limited async updates**

1. Cache the URL in Redis
2. Increment click count in Redis (atomic INCR)
3. Periodically flush to PostgreSQL (every 30 seconds)

```java
// On redirect
redisTemplate.opsForValue().increment("clicks:" + shortCode);

// Scheduled job every 30 seconds
@Scheduled(fixedRate = 30000)
public void flushClickCounts() {
    // Read all click counts from Redis
    // Batch update to PostgreSQL
    // Clear Redis counters
}
```

This reduces database writes from 1000/sec to 1 every 30 seconds."

---

### Edge Cases & Error Handling

---

**Q19: What edge cases do you handle?**

**Answer:**
"Several edge cases:

1. **Invalid URL:** Validate format before accepting
   ```java
   if (!url.matches("^https?://.*")) {
       throw new BadRequestException("Invalid URL format");
   }
   ```

2. **URL too long:** PostgreSQL TEXT handles it, but set reasonable limits

3. **Expired links:** Return 410 Gone, not 404

4. **Malicious URLs:** Could add URL scanning/blacklist

5. **Code collision:** Retry with new code

6. **Short code not found:** Return 404 with helpful message

7. **Unauthorized access to private URL:** Return 403 Forbidden"

---

**Q20: How do you prevent abuse (spam, malicious links)?**

**Answer:**
"Multiple layers of protection:

1. **Rate limiting:** Max URLs per user per hour
   
2. **Authentication:** Require login to create URLs

3. **URL validation:** Check format, block known malicious domains

4. **CAPTCHA:** For anonymous users (if allowed)

5. **Reporting:** Let users report malicious links

6. **Monitoring:** Alert on unusual patterns (1 user creating 1000 URLs)

For this project, I implemented rate limiting and authentication. Production would add malware scanning."

---

## How to Whiteboard This System

### Step 1: Clarify Requirements (2 min)
- "How short should URLs be?"
- "Do we need click tracking?"
- "Should links expire?"
- "User accounts or anonymous?"

### Step 2: High-Level Design (5 min)
```
Client → API → Generate Code → DB → Return Short URL
Client → Short URL → Lookup → Redirect
```

### Step 3: Deep Dive (8 min)
Pick ONE based on interviewer interest:
- Code generation algorithm
- Database schema & indexes
- Scaling strategy

### Step 4: Trade-offs (2 min)
- Random vs. sequential codes
- 301 vs. 302 redirects
- SQL vs. NoSQL

---

## Keywords to Use in Interviews

**Database:**
- B-tree index, O(log n) lookup
- Connection pooling (HikariCP)
- ACID compliance

**Security:**
- BCrypt, salting, hashing
- HTTPS, secure cookies
- Rate limiting

**Scaling:**
- Caching, Redis
- Read replicas
- Horizontal scaling

**HTTP:**
- 302 Found, 301 Moved Permanently
- 404 Not Found, 410 Gone
- RESTful API design

---

## Quick Reference Card

| Question | Your Answer |
|----------|-------------|
| **Why 6 characters?** | 62^6 = 56 billion combinations |
| **Why random, not sequential?** | Security - can't guess URLs |
| **How handle collisions?** | Check DB, regenerate if exists |
| **Why 302, not 301?** | Need click tracking, 301 gets cached |
| **How store passwords?** | BCrypt with built-in salt |
| **Most frequent query?** | Lookup by short_code (indexed) |
| **How handle expiration?** | Check expires_at timestamp |
| **How scale?** | Cache hot URLs in Redis |

---

*Good luck with your interviews! 🚀*
