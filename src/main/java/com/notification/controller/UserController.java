package com.notification.controller;

// =====================================================
// UserController.java - User Management REST API
// =====================================================
//
// REST endpoints for user operations.
// Provides cached user lookups for testing purposes.
//

import com.notification.dto.request.CreateUserRequest;
import com.notification.dto.response.ApiResponse;
import com.notification.model.entity.User;
import com.notification.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST controller for user operations.
 *
 * Provides endpoints for testing user caching functionality.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "User lookup and management operations")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Find user by email.
     *
     * @Cacheable - First request hits database, subsequent requests serve from Redis cache.
     */
    @GetMapping("/email/{email}")
    @Operation(summary = "Find user by email", description = "Retrieve user information by email address with caching")
    public ResponseEntity<ApiResponse<User>> getUserByEmail(@PathVariable String email) {
        User user = userService.findByEmail(email);
        return ResponseEntity.ok(ApiResponse.success("User found", user));
    }

    /**
     * Find user by phone.
     *
     * @Cacheable - First request hits database, subsequent requests serve from Redis cache.
     */
    @GetMapping("/phone/{phone}")
    @Operation(summary = "Find user by phone", description = "Retrieve user information by phone number with caching")
    public ResponseEntity<ApiResponse<User>> getUserByPhone(@PathVariable String phone) {
        User user = userService.findByPhone(phone);
        return ResponseEntity.ok(ApiResponse.success("User found", user));
    }

    /**
     * Get all users with device tokens for push notifications.
     *
     * @Cacheable - Caches the list of users who can receive push notifications.
     */
    @GetMapping("/push-eligible")
    @Operation(summary = "Get push-eligible users", description = "Retrieve all users with device tokens for push notifications")
    public ResponseEntity<ApiResponse<List<User>>> getPushEligibleUsers() {
        List<User> users = userService.findUsersWithDeviceTokens();
        return ResponseEntity.ok(ApiResponse.success("Push-eligible users retrieved", users));
    }

    /**
     * Get all users.
     */
    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieve all users in the system")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.findAllUsers();
        return ResponseEntity.ok(ApiResponse.success("All users retrieved", users));
    }

    /**
     * Create a user if not exists.
     */
    @PostMapping
    @Operation(summary = "Create user if not exists", description = "Create a new user or return existing user with the same email")
    public ResponseEntity<ApiResponse<User>> createUserIfNotExists(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUserIfNotExists(request.getEmail(), request.getPhone(), request.getDeviceToken());
        return ResponseEntity.ok(ApiResponse.success("User created or retrieved", user));
    }
}