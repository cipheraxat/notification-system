-- =====================================================
-- V1__init_schema.sql - Initial Database Schema
-- =====================================================
--
-- This is a Flyway migration script.
-- 
-- Naming convention: V{version}__{description}.sql
-- - V1 = Version 1 (first migration)
-- - __ = Two underscores (required separator)
-- - init_schema = Human-readable description
--
-- Flyway will:
-- 1. Check if this migration has been run before
-- 2. If not, execute it and record in flyway_schema_history table
-- 3. Never run the same migration twice
--

-- =====================================================
-- Enable UUID Extension
-- =====================================================
-- UUIDs are better than auto-increment IDs because:
-- 1. They're globally unique (no collisions across systems)
-- 2. Can be generated without database round-trip
-- 3. Don't reveal how many records you have
--
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- TABLE: users
-- =====================================================
-- Stores user information for sending notifications.
-- Each user can have an email, phone, and device token.
--
-- In a real system, this would likely be a reference to
-- a user service, but we include it here for simplicity.
--
CREATE TABLE users (
    -- Primary Key: Unique identifier for each user
    -- uuid_generate_v4() creates a random UUID
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- User's email address (for email notifications)
    -- UNIQUE ensures no duplicate emails
    email VARCHAR(255) UNIQUE,
    
    -- User's phone number (for SMS notifications)
    -- Format: E.164 international format (+1234567890)
    phone VARCHAR(20),
    
    -- Device token for push notifications
    -- This comes from the mobile app (Firebase/APNs)
    device_token VARCHAR(500),
    
    -- Timestamps for auditing
    -- CURRENT_TIMESTAMP gets the current date/time
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index on email for fast lookups
-- When we query "WHERE email = ?", this makes it fast
CREATE INDEX idx_users_email ON users(email);

-- Index on phone for fast lookups
CREATE INDEX idx_users_phone ON users(phone);

-- =====================================================
-- TABLE: user_preferences
-- =====================================================
-- Stores notification preferences for each user.
-- Users can:
-- 1. Enable/disable specific channels
-- 2. Set quiet hours (no notifications during sleep)
--
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Foreign Key: Links to users table
    -- ON DELETE CASCADE: If user is deleted, preferences are too
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- Which channel this preference is for
    -- Values: EMAIL, SMS, PUSH, IN_APP
    channel VARCHAR(20) NOT NULL,
    
    -- Is this channel enabled?
    -- true = send notifications, false = don't send
    enabled BOOLEAN DEFAULT true,
    
    -- Quiet hours: Don't disturb during these times
    -- Example: 22:00 to 08:00 (10 PM to 8 AM)
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Composite unique constraint
    -- Each user can only have ONE preference per channel
    UNIQUE(user_id, channel)
);

-- Index for fast lookup by user
CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);

-- =====================================================
-- TABLE: notification_templates
-- =====================================================
-- Stores reusable message templates with placeholders.
-- 
-- Example template:
--   Name: "welcome-email"
--   Subject: "Welcome, {{userName}}!"
--   Body: "Hi {{userName}}, thanks for joining..."
--
-- The {{placeholders}} get replaced with actual values
-- when sending the notification.
--
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Unique name to identify the template
    -- Example: "order-confirmation", "password-reset"
    name VARCHAR(100) NOT NULL UNIQUE,
    
    -- Which channel this template is for
    -- Templates are channel-specific because content differs
    -- (email has subject, SMS is short, etc.)
    channel VARCHAR(20) NOT NULL,
    
    -- Subject line template (mainly for email)
    -- Can contain placeholders: "Order #{{orderId}} Confirmed!"
    subject_template VARCHAR(500),
    
    -- Message body template
    -- Example: "Hi {{userName}}, your order is ready."
    body_template TEXT NOT NULL,
    
    -- Soft delete: Mark template as inactive instead of deleting
    -- This preserves history for notifications that used it
    is_active BOOLEAN DEFAULT true,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for looking up templates by name
CREATE INDEX idx_templates_name ON notification_templates(name);

-- Index for filtering by channel
CREATE INDEX idx_templates_channel ON notification_templates(channel);

-- =====================================================
-- TABLE: notifications (MAIN TABLE)
-- =====================================================
-- This is the heart of our system!
-- Every notification sent goes into this table.
--
-- Status flow:
--   PENDING → PROCESSING → SENT → DELIVERED
--                    ↓
--                  FAILED (after retries exhausted)
--
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Who is this notification for?
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- Which template was used? (optional)
    -- Can be NULL if sending without a template
    template_id UUID REFERENCES notification_templates(id),
    
    -- ==================== Delivery Settings ====================
    
    -- How to deliver: EMAIL, SMS, PUSH, or IN_APP
    channel VARCHAR(20) NOT NULL,
    
    -- Priority determines processing order:
    -- HIGH   = Process immediately (e.g., OTP, password reset)
    -- MEDIUM = Normal priority (e.g., order updates)
    -- LOW    = Can wait (e.g., marketing, weekly digest)
    priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    
    -- ==================== Content ====================
    
    -- The actual message (after template processing)
    subject VARCHAR(500),        -- For email
    content TEXT NOT NULL,       -- Message body
    
    -- ==================== Status Tracking ====================
    
    -- Current state of the notification
    -- PENDING     = Waiting to be processed
    -- PROCESSING  = Currently being sent
    -- SENT        = Successfully sent to provider
    -- DELIVERED   = Confirmed delivered to user
    -- FAILED      = All retries exhausted
    -- READ        = User has read it (in-app only)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    -- ==================== Retry Handling ====================
    
    -- How many times we've tried to send
    retry_count INTEGER DEFAULT 0,
    
    -- Maximum attempts before giving up
    max_retries INTEGER DEFAULT 3,
    
    -- When to try again (if previous attempt failed)
    next_retry_at TIMESTAMP WITH TIME ZONE,
    
    -- Error message from the last failure
    -- Helps with debugging
    error_message TEXT,
    
    -- ==================== Timestamps ====================
    
    -- When was it sent to the provider?
    sent_at TIMESTAMP WITH TIME ZONE,
    
    -- When was it confirmed delivered?
    delivered_at TIMESTAMP WITH TIME ZONE,
    
    -- When did the user read it? (in-app only)
    read_at TIMESTAMP WITH TIME ZONE,
    
    -- When was this record created?
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ==================== Indexes for Notifications ====================
-- Indexes speed up queries but slow down inserts
-- Only add indexes for columns you query frequently

-- Find all notifications for a user (for inbox)
CREATE INDEX idx_notifications_user_id ON notifications(user_id);

-- Filter by status (for processing PENDING notifications)
CREATE INDEX idx_notifications_status ON notifications(status);

-- Filter by channel (for analytics)
CREATE INDEX idx_notifications_channel ON notifications(channel);

-- Find notifications that need retry
-- Partial index: Only includes rows where condition is true
CREATE INDEX idx_notifications_retry ON notifications(next_retry_at) 
    WHERE status = 'PENDING' AND next_retry_at IS NOT NULL;

-- For inbox pagination: user's notifications ordered by time
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);

-- =====================================================
-- SEED DATA: Insert some test data
-- =====================================================
-- This helps us test the API without creating data manually.

-- Insert test users
INSERT INTO users (id, email, phone, device_token) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'john@example.com', '+1234567890', 'device_token_john'),
    ('550e8400-e29b-41d4-a716-446655440002', 'jane@example.com', '+1987654321', 'device_token_jane'),
    ('550e8400-e29b-41d4-a716-446655440003', 'bob@example.com', '+1555555555', NULL);

-- Insert user preferences
INSERT INTO user_preferences (user_id, channel, enabled, quiet_hours_start, quiet_hours_end) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'EMAIL', true, NULL, NULL),
    ('550e8400-e29b-41d4-a716-446655440001', 'SMS', true, '22:00', '08:00'),
    ('550e8400-e29b-41d4-a716-446655440001', 'PUSH', true, '22:00', '08:00'),
    ('550e8400-e29b-41d4-a716-446655440002', 'EMAIL', true, NULL, NULL),
    ('550e8400-e29b-41d4-a716-446655440002', 'SMS', false, NULL, NULL);

-- Insert notification templates
INSERT INTO notification_templates (id, name, channel, subject_template, body_template) VALUES
    ('660e8400-e29b-41d4-a716-446655440001', 'welcome-email', 'EMAIL', 
     'Welcome to Our Platform, {{userName}}!',
     'Hi {{userName}},\n\nThank you for joining our platform! We''re excited to have you.\n\nBest regards,\nThe Team'),
    
    ('660e8400-e29b-41d4-a716-446655440002', 'order-confirmation', 'EMAIL',
     'Order #{{orderId}} Confirmed',
     'Hi {{userName}},\n\nYour order #{{orderId}} has been confirmed!\n\nTotal: {{orderTotal}}\n\nThank you for shopping with us!'),
    
    ('660e8400-e29b-41d4-a716-446655440003', 'otp-sms', 'SMS',
     NULL,
     'Your verification code is {{otpCode}}. Valid for 10 minutes. Do not share this code.'),
    
    ('660e8400-e29b-41d4-a716-446655440004', 'order-shipped', 'PUSH',
     'Your Order is On Its Way!',
     'Order #{{orderId}} has been shipped. Track: {{trackingUrl}}');
