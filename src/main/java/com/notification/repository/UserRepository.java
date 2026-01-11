package com.notification.repository;

// =====================================================
// UserRepository.java - Data Access for Users
// =====================================================
//
// Repository = The layer that talks to the database.
// 
// Spring Data JPA is MAGIC! You just:
// 1. Define an interface extending JpaRepository
// 2. Declare method names following a naming convention
// 3. Spring generates the implementation automatically!
//
// No SQL writing needed for most operations.
//

import com.notification.model.entity.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity operations.
 * 
 * Extends JpaRepository which provides:
 * - save(User) - Insert or update
 * - findById(UUID) - Find by primary key
 * - findAll() - Get all users
 * - deleteById(UUID) - Delete by primary key
 * - count() - Count total records
 * - existsById(UUID) - Check if exists
 * - And many more!
 * 
 * JpaRepository<User, UUID> means:
 * - User = The entity type
 * - UUID = The type of the primary key (id)
 * 
 * @Repository annotation tells Spring this is a bean for data access.
 * It also enables exception translation (converts SQL exceptions
 * to Spring's DataAccessException).
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // ==================== Query Methods ====================
    // 
    // Spring Data JPA generates queries from method names!
    // 
    // Naming Convention:
    //   findBy{PropertyName} → WHERE property_name = ?
    //   findBy{Prop1}And{Prop2} → WHERE prop1 = ? AND prop2 = ?
    //   findBy{Prop}OrderBy{Prop2}Desc → ORDER BY prop2 DESC
    //
    
    /**
     * Find a user by email address.
     * 
     * Spring generates: SELECT * FROM users WHERE email = ?
     * 
     * Returns Optional because the user might not exist.
     * Optional<User> is better than returning null because:
     * - Forces caller to handle the "not found" case
     * - Prevents NullPointerException
     * 
     * Usage:
     *   userRepository.findByEmail("john@example.com")
     *       .ifPresent(user -> System.out.println(user.getId()));
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find a user by phone number.
     * 
     * Spring generates: SELECT * FROM users WHERE phone = ?
     */
    Optional<User> findByPhone(String phone);
    
    /**
     * Check if a user exists with the given email.
     * 
     * Spring generates: SELECT COUNT(*) > 0 FROM users WHERE email = ?
     * 
     * Returns boolean, more efficient than findByEmail()
     * when you only need to check existence.
     */
    boolean existsByEmail(String email);
    
    /**
     * Find users who have a device token (can receive push notifications).
     * 
     * The "IsNotNull" suffix generates: WHERE device_token IS NOT NULL
     */
    List<User> findByDeviceTokenIsNotNull();
    
    // ==================== Custom JPQL Queries ====================
    //
    // For complex queries, you can write JPQL (Java Persistence Query Language)
    // JPQL is like SQL but uses entity/field names, not table/column names.
    //
    
    /**
     * Find all users who have a preference enabled for a specific channel.
     * 
     * This query:
     * 1. Joins User with UserPreference
     * 2. Filters by channel and enabled status
     * 
     * @Query annotation allows writing custom JPQL.
     * 
     * JPQL syntax:
     * - u.preferences = property on User entity
     * - JOIN = Inner join
     * - p.channel = property on UserPreference entity
     * - :channel = Named parameter (passed from method argument)
     */
    @Query("SELECT u FROM User u JOIN u.preferences p " +
           "WHERE p.channel = :channel AND p.enabled = true")
    List<User> findUsersWithChannelEnabled(
        @org.springframework.data.repository.query.Param("channel") 
        com.notification.model.enums.ChannelType channel
    );
    
    /**
     * Count users by email domain.
     * 
     * Useful for analytics: How many users from each domain?
     * 
     * Example: countByEmailDomain("gmail.com") → 500
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.email LIKE %:domain")
    long countByEmailDomain(@org.springframework.data.repository.query.Param("domain") String domain);
}
