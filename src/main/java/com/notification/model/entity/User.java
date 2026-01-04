package com.notification.model.entity;

// =====================================================
// User.java - User Entity
// =====================================================
//
// An Entity is a Java class that maps to a database table.
// Each instance of this class represents one row in the table.
//
// JPA (Java Persistence API) annotations tell Hibernate how
// to convert between Java objects and database rows.
//

// JPA Annotations (for database mapping)
import jakarta.persistence.*;

// Lombok Annotations (for reducing boilerplate code)
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Java utilities
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User Entity - Represents a user who can receive notifications.
 * 
 * Maps to the 'users' table in the database.
 * 
 * Lombok Annotations Explained:
 * 
 * @Data - Generates:
 *   - Getters for all fields (getName(), getEmail(), etc.)
 *   - Setters for all non-final fields (setName(), setEmail(), etc.)
 *   - toString() method
 *   - equals() and hashCode() methods
 * 
 * @Builder - Generates the Builder pattern for creating objects:
 *   User user = User.builder()
 *       .email("john@example.com")
 *       .phone("+1234567890")
 *       .build();
 * 
 * @NoArgsConstructor - Generates a no-argument constructor:
 *   User user = new User();
 * 
 * @AllArgsConstructor - Generates a constructor with all fields:
 *   User user = new User(id, email, phone, ...);
 */
@Entity                          // Marks this class as a JPA entity
@Table(name = "users")           // Maps to the "users" table
@Data                            // Lombok: getters, setters, toString, etc.
@Builder                         // Lombok: builder pattern
@NoArgsConstructor               // Lombok: no-arg constructor
@AllArgsConstructor              // Lombok: all-args constructor
public class User {

    // ==================== Primary Key ====================
    
    /**
     * Unique identifier for this user.
     * 
     * @Id - Marks this as the primary key
     * 
     * @GeneratedValue - Tells JPA how to generate the ID
     *   GenerationType.AUTO - Let the database/JPA decide
     *   For PostgreSQL with UUID, this uses uuid_generate_v4()
     * 
     * @Column - Specifies column details
     *   updatable = false - ID should never change after creation
     *   nullable = false - ID cannot be null
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    // ==================== Contact Information ====================
    
    /**
     * User's email address for email notifications.
     * 
     * @Column specifications:
     *   unique = true - No two users can have the same email
     *   length = 255 - Maximum characters allowed
     */
    @Column(name = "email", unique = true, length = 255)
    private String email;
    
    /**
     * User's phone number for SMS notifications.
     * Format: E.164 international format (+1234567890)
     */
    @Column(name = "phone", length = 20)
    private String phone;
    
    /**
     * Device token for push notifications.
     * This is obtained from Firebase Cloud Messaging (FCM) or
     * Apple Push Notification Service (APNs).
     */
    @Column(name = "device_token", length = 500)
    private String deviceToken;
    
    // ==================== Relationships ====================
    
    /**
     * User's notification preferences.
     * 
     * @OneToMany - One user has many preferences (one per channel)
     * 
     * mappedBy = "user" - The UserPreference entity owns the relationship
     *   (it has the foreign key column "user_id")
     * 
     * cascade = CascadeType.ALL - If we save/delete a user, 
     *   also save/delete their preferences
     * 
     * orphanRemoval = true - If we remove a preference from this list,
     *   delete it from the database too
     * 
     * fetch = FetchType.LAZY - Don't load preferences until we access them
     *   This is more efficient when we don't always need preferences
     */
    @OneToMany(
        mappedBy = "user",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default  // Tells Lombok to use this default in the builder
    private List<UserPreference> preferences = new ArrayList<>();
    
    /**
     * Notifications sent to this user.
     * 
     * Similar to preferences, but for notifications.
     * We use LAZY loading because a user might have thousands
     * of notifications, and we don't want to load them all
     * every time we load a user.
     */
    @OneToMany(
        mappedBy = "user",
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Notification> notifications = new ArrayList<>();
    
    // ==================== Timestamps ====================
    
    /**
     * When this user was created.
     * 
     * @Column(updatable = false) - Once set, this never changes
     */
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
    
    /**
     * When this user was last updated.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    // ==================== JPA Lifecycle Callbacks ====================
    
    /**
     * Called automatically BEFORE inserting into the database.
     * 
     * @PrePersist is a JPA lifecycle callback.
     * We use it to set timestamps automatically.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
    
    /**
     * Called automatically BEFORE updating in the database.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Add a preference to this user.
     * 
     * This is a convenience method that properly sets up
     * both sides of the relationship.
     */
    public void addPreference(UserPreference preference) {
        preferences.add(preference);
        preference.setUser(this);
    }
    
    /**
     * Remove a preference from this user.
     */
    public void removePreference(UserPreference preference) {
        preferences.remove(preference);
        preference.setUser(null);
    }
}
