-- Remove uuid_generate_v4 defaults so ID generation is handled in application code
-- with UUIDv7 (time-ordered IDs).

ALTER TABLE users
    ALTER COLUMN id DROP DEFAULT;

ALTER TABLE user_preferences
    ALTER COLUMN id DROP DEFAULT;

ALTER TABLE notification_templates
    ALTER COLUMN id DROP DEFAULT;

ALTER TABLE notifications
    ALTER COLUMN id DROP DEFAULT;
