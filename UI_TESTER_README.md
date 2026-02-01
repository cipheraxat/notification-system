# Notification System UI Tester

A comprehensive web interface to test all notification system endpoints end-to-end.

## Features

### 🔔 Send Single Notifications
- Test all notification channels (Email, SMS, Push, In-App)
- Use templates with variables or direct content
- Set priority levels (LOW, NORMAL, HIGH, URGENT)
- Add event IDs for deduplication testing

### 📨 Send Bulk Notifications
- Send the same notification to multiple users
- Perfect for testing marketing campaigns or system announcements
- Support for template variables and direct content

### 📚 Notification History & Management
- **Get Notification by ID** - Retrieve specific notification details
- **User Inbox** - View all notifications for a user
- **Unread Count** - Check unread notification count
- **Mark as Read** - Mark individual notifications as read
- **Mark All as Read** - Mark all user notifications as read
- **History Search** - Filter notifications by user and channel

### 🎨 Template Management
- **Create Templates** - Add new notification templates
- **Get by ID/Name** - Retrieve specific templates
- **Update Templates** - Modify existing templates
- **Delete Templates** - Soft delete templates
- **List Templates** - View all templates with channel filtering

### 👥 User Management
- **Find by Email** - Lookup users by email address
- **Find by Phone** - Lookup users by phone number
- **Push-Eligible Users** - Get all users with device tokens for push notifications

### ❤️ Health Monitoring
- **Basic Health Check** - Simple service availability check
- **Detailed Health Check** - Monitor database, Redis, and Kafka connectivity

## Complete API Endpoint Coverage

| Category | Endpoint | Method | UI Feature |
|----------|----------|--------|------------|
| **Notifications** | `/api/v1/notifications` | POST | Send Single |
| **Notifications** | `/api/v1/notifications/bulk` | POST | Send Bulk |
| **Notifications** | `/api/v1/notifications/{id}` | GET | Get by ID |
| **Notifications** | `/api/v1/notifications/user/{userId}` | GET | User Inbox |
| **Notifications** | `/api/v1/notifications/user/{userId}/unread-count` | GET | Unread Count |
| **Notifications** | `/api/v1/notifications/{id}/read` | PATCH | Mark as Read |
| **Notifications** | `/api/v1/notifications/user/{userId}/read-all` | PATCH | Mark All as Read |
| **Templates** | `/api/v1/templates` | POST | Create Template |
| **Templates** | `/api/v1/templates` | GET | List Templates |
| **Templates** | `/api/v1/templates/{id}` | GET | Get by ID |
| **Templates** | `/api/v1/templates/name/{name}` | GET | Get by Name |
| **Templates** | `/api/v1/templates/{id}` | PUT | Update Template |
| **Templates** | `/api/v1/templates/{id}` | DELETE | Delete Template |
| **Users** | `/api/v1/users/email/{email}` | GET | Find by Email |
| **Users** | `/api/v1/users/phone/{phone}` | GET | Find by Phone |
| **Users** | `/api/v1/users/push-eligible` | GET | Push-Eligible Users |
| **Health** | `/api/v1/health` | GET | Basic Health |
| **Health** | `/api/v1/health/detailed` | GET | Detailed Health |

## How to Use

### Option 1: With Full Infrastructure (Recommended)
```bash
# Start all services (PostgreSQL, Redis, Kafka)
docker-compose up -d

# Start the application
mvn spring-boot:run
```

### Option 2: Quick Development Testing
```bash
# Start with H2 in-memory database (no external dependencies)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Access the UI
- Visit: **http://localhost:8080**
- The UI loads automatically at the root path

## Testing Scenarios

### 1. Single Notification Flow
1. Go to "Send Single" tab
2. Select channel (Email/SMS/Push/In-App)
3. Choose template or enter direct content
4. Click "Send Notification"
5. Check response and status

### 2. Bulk Notification Campaign
1. Go to "Send Bulk" tab
2. Enter multiple user IDs (comma-separated)
3. Configure notification content
4. Click "Send Bulk Notifications"
5. Review bulk response summary

### 3. User Inbox Management
1. Go to "History" tab
2. Enter user ID in "User Inbox" section
3. Click "Get User Inbox" to see all notifications
4. Click "Get Unread Count" for unread summary
5. Use "Mark as Read" to update notification status

### 4. Template Lifecycle
1. Go to "Templates" tab
2. Create new template in "Create New Template" section
3. Retrieve template by ID or name
4. Update template content
5. Delete template when no longer needed

### 5. User Lookup Testing
1. Go to "Users" tab
2. Test email and phone lookups
3. View push-eligible users for push notification testing

## Sample Test Data

## How to Use

1. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

2. **Open the tester:**
   - Visit: `http://localhost:8080`
   - The UI will load automatically

3. **Test notifications:**
   - Use the pre-filled test user ID: `550e8400-e29b-41d4-a716-446655440001`
   - Try different channels and priorities
   - Check the response and status

## Sample Test Data

### Test User IDs
- `550e8400-e29b-41d4-a716-446655440001` (has email, phone, device token)
- `550e8400-e29b-41d4-a716-446655440002`
- `550e8400-e29b-41d4-a716-446655440003`

### Test Templates
- `welcome-email` - Welcome email with userName variable
- `otp-sms` - OTP SMS with otpCode variable

### Test Scenarios

1. **Basic Email Notification:**
   - Channel: Email
   - Content: "Hello from the notification system!"

2. **Template Email:**
   - Use Template: ✅
   - Template Name: `welcome-email`
   - Variables: `userName` = "John Doe"

3. **SMS with Deduplication:**
   - Channel: SMS
   - Event ID: `test-event-123`
   - Send twice to test deduplication

4. **Bulk Push Notifications:**
   - Add multiple user IDs
   - Channel: Push
   - Content: "System maintenance in 30 minutes"

## API Endpoints Tested

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/notifications` | POST | Send single notification |
| `/api/v1/notifications/bulk` | POST | Send bulk notifications |
| `/api/v1/notifications/user/{userId}` | GET | Get user notifications |
| `/api/v1/templates` | GET | List templates |
| `/api/v1/health` | GET | Basic health check |
| `/api/v1/health/detailed` | GET | Detailed health check |

## Troubleshooting

### Notifications Stuck in PENDING
- Check if Kafka is running
- Check consumer logs for errors
- Notifications will be retried automatically

### Rate Limit Errors
- Wait for the rate limit window to reset (1 hour)
- Or use a different user ID
- Check Redis for rate limit counters

### Template Errors
- Ensure template exists and is active
- Check variable names match template placeholders
- Verify channel compatibility

### Health Check Failures
- Database: Check PostgreSQL connection
- Redis: Check Redis server status
- Kafka: Check Kafka broker connectivity

## Development Notes

- The UI uses vanilla JavaScript (no frameworks)
- All API calls include proper error handling
- Responses are displayed in formatted JSON
- The interface is responsive and works on mobile devices

## Security Note

This UI is for testing purposes only. In production:
- Remove or secure the static files
- Use proper authentication
- Implement rate limiting on the UI endpoints
- Add input validation and sanitization

---

## ✅ Complete API Coverage

This UI tester now provides **complete coverage** of all notification system endpoints:

- **19 total endpoints** across 4 controllers
- **All CRUD operations** for templates and notifications
- **User management** and lookup functionality
- **Health monitoring** with dependency checks
- **Real-time testing** without external tools

**Ready for comprehensive end-to-end testing! 🚀**