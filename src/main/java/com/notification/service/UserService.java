package com.notification.service;

// =====================================================
// UserService.java - User Management Service
// =====================================================
//
// Service for user-related operations with caching support.
// Implements Redis caching for user lookups by email and phone
// following Alex Xu's system design principles.
//

import com.notification.exception.ResourceNotFoundException;
import com.notification.model.entity.User;
import com.notification.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.util.List;

/**
 * Service for user operations.
 *
 * Provides cached user lookups for email and phone to reduce database load.
 * Caching follows the pattern: User Request → Controller → Service → Redis Cache → PostgreSQL
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Find user by email with caching.
     *
     * @Cacheable enables Redis caching with key "users::email:{email}"
     * First request hits database and caches result.
     * Subsequent requests serve from cache without database queries.
     *
     * @param email User's email address
     * @return User entity if found
     * @throws ResourceNotFoundException if user not found
     */
    @Cacheable(value = "users", key = "'email:' + #email")
    public User findByEmail(String email) {
        logger.debug("Looking up user by email: {}", email);

        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    /**
     * Find user by phone with caching.
     *
     * @Cacheable enables Redis caching with key "users::phone:{phone}"
     * First request hits database and caches result.
     * Subsequent requests serve from cache without database queries.
     *
     * @param phone User's phone number
     * @return User entity if found
     * @throws ResourceNotFoundException if user not found
     */
    @Cacheable(value = "users", key = "'phone:' + #phone")
    public User findByPhone(String phone) {
        logger.debug("Looking up user by phone: {}", phone);

        return userRepository.findByPhone(phone)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with phone: " + phone));
    }

    /**
     * Find user by ID with caching.
     *
     * @Cacheable enables Redis caching with key "users::id:{id}"
     * First request hits database and caches result.
     * Subsequent requests serve from Redis cache.
     *
     * @param id User's UUID
     * @return User entity if found
     * @throws ResourceNotFoundException if user not found
     */
    @Cacheable(value = "users", key = "'id:' + #id")
    public User findById(UUID id) {
        logger.debug("Looking up user by ID: {}", id);

        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    /**
     * Find all users with device tokens for push notifications.
     *
     * @Cacheable enables Redis caching with key "users::deviceTokens"
     * This caches the list of users who can receive push notifications.
     *
     * @return List of users with device tokens
     */
    @Cacheable(value = "users", key = "'deviceTokens'")
    public List<User> findUsersWithDeviceTokens() {
        logger.debug("Looking up users with device tokens for push notifications");

        return userRepository.findByDeviceTokenIsNotNull();
    }

    /**
     * Evict user cache when user email is updated.
     *
     * @CacheEvict removes the cached entry for the old email.
     * Should be called when updating a user's email.
     *
     * @param oldEmail The old email to evict from cache
     */
    @CacheEvict(value = "users", key = "'email:' + #oldEmail")
    public void evictUserCacheByEmail(String oldEmail) {
        logger.debug("Evicting user cache for email: {}", oldEmail);
    }

    /**
     * Evict user cache when user phone is updated.
     *
     * @CacheEvict removes the cached entry for the old phone.
     * Should be called when updating a user's phone.
     *
     * @param oldPhone The old phone to evict from cache
     */
    @CacheEvict(value = "users", key = "'phone:' + #oldPhone")
    public void evictUserCacheByPhone(String oldPhone) {
        logger.debug("Evicting user cache for phone: {}", oldPhone);
    }

    /**
     * Evict device tokens cache when user device token is updated.
     *
     * @CacheEvict removes the cached list of users with device tokens.
     * Should be called when adding/removing device tokens.
     */
    @CacheEvict(value = "users", key = "'deviceTokens'")
    public void evictDeviceTokensCache() {
        logger.debug("Evicting device tokens cache");
    }

    /**
     * Get all users.
     *
     * @return List of all users
     */
    public List<User> findAllUsers() {
        logger.debug("Getting all users");
        return userRepository.findAll();
    }

    /**
     * Create a user if not exists, or return existing user.
     *
     * @param email User's email
     * @param phone User's phone (optional)
     * @param deviceToken Device token for push notifications (optional)
     * @return The user entity
     */
    @Transactional
    public User createUserIfNotExists(String email, String phone, String deviceToken) {
        logger.debug("Creating user if not exists: {}", email);

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            logger.debug("User already exists with email: {}", email);
            return existingUser.get();
        }

        User newUser = User.builder()
            .email(email)
            .phone(phone)
            .deviceToken(deviceToken)
            .build();

        User savedUser = userRepository.save(newUser);
        logger.info("Created new user with email: {}", email);
        return savedUser;
    }
}