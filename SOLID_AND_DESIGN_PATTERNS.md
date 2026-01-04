# SOLID Principles & Design Patterns in Java

> A comprehensive guide with detailed commentary and practical Java examples.

---

## Table of Contents

1. [SOLID Principles](#solid-principles)
   - [S - Single Responsibility Principle](#s---single-responsibility-principle)
   - [O - Open/Closed Principle](#o---openclosed-principle)
   - [L - Liskov Substitution Principle](#l---liskov-substitution-principle)
   - [I - Interface Segregation Principle](#i---interface-segregation-principle)
   - [D - Dependency Inversion Principle](#d---dependency-inversion-principle)
2. [Design Patterns](#design-patterns)
   - [Creational Patterns](#creational-patterns)
     - [Singleton](#1-singleton-pattern)
     - [Factory Method](#2-factory-method-pattern)
     - [Builder](#3-builder-pattern)
   - [Structural Patterns](#structural-patterns)
     - [Adapter](#4-adapter-pattern)
     - [Decorator](#5-decorator-pattern)
   - [Behavioral Patterns](#behavioral-patterns)
     - [Strategy](#6-strategy-pattern)
     - [Observer](#7-observer-pattern)
     - [Template Method](#8-template-method-pattern)
3. [12-Factor App Principles](#12-factor-app-principles)
4. [Interview Quick Reference](#interview-quick-reference)

---

# SOLID Principles

SOLID is an acronym for five design principles that make software more understandable, flexible, and maintainable.

---

## S - Single Responsibility Principle

> **"A class should have only one reason to change."**

Each class should do ONE thing and do it well.

### ❌ Bad Example - Multiple Responsibilities

```java
/**
 * BAD: This class does too many things!
 * 
 * It handles:
 * 1. User data (name, email)
 * 2. Database operations (save)
 * 3. Email sending
 * 4. Report generation
 * 
 * If email logic changes, we modify this class.
 * If database changes, we modify this class.
 * If report format changes, we modify this class.
 * 
 * This is a maintenance nightmare!
 */
public class User {
    private String name;
    private String email;
    
    // Responsibility 1: User data
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    // Responsibility 2: Database operations
    public void saveToDatabase() {
        // Connect to database
        // Execute INSERT query
        // Handle connection errors
    }
    
    // Responsibility 3: Email operations
    public void sendWelcomeEmail() {
        // Configure SMTP
        // Build email template
        // Send email
    }
    
    // Responsibility 4: Report generation
    public String generateReport() {
        // Format user data as PDF
        // Add headers and footers
        return "User Report...";
    }
}
```

### ✅ Good Example - Single Responsibility Each

```java
/**
 * GOOD: User class only handles user data.
 * 
 * This class has ONE reason to change:
 * - If user properties change (add phone number, etc.)
 * 
 * It does NOT know about databases, emails, or reports.
 */
public class User {
    private String name;
    private String email;
    
    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}

/**
 * GOOD: Separate class for database operations.
 * 
 * This class has ONE reason to change:
 * - If database logic changes (switch from MySQL to PostgreSQL)
 */
public class UserRepository {
    
    public void save(User user) {
        // Only handles database operations
        // INSERT INTO users (name, email) VALUES (?, ?)
    }
    
    public User findById(Long id) {
        // SELECT * FROM users WHERE id = ?
        return new User("John", "john@example.com");
    }
    
    public void delete(User user) {
        // DELETE FROM users WHERE id = ?
    }
}

/**
 * GOOD: Separate class for email operations.
 * 
 * This class has ONE reason to change:
 * - If email provider changes (SendGrid to SES)
 * - If email templates change
 */
public class EmailService {
    
    public void sendWelcomeEmail(User user) {
        // Only handles email sending
        String to = user.getEmail();
        String subject = "Welcome!";
        String body = "Hello " + user.getName();
        // Send via email provider
    }
    
    public void sendPasswordReset(User user, String token) {
        // Send password reset email
    }
}

/**
 * GOOD: Separate class for report generation.
 * 
 * This class has ONE reason to change:
 * - If report format changes (PDF to Excel)
 */
public class UserReportGenerator {
    
    public String generateReport(User user) {
        // Only handles report generation
        return "User Report for: " + user.getName();
    }
}
```

### 📝 Interview Tip

> **"SRP means each class has one job. In my notification system, I separated `NotificationService` (business logic), `NotificationRepository` (database), and `EmailChannelHandler` (sending emails). If I need to change how emails are sent, I only touch the handler."**

---

## O - Open/Closed Principle

> **"Software entities should be open for extension, but closed for modification."**

You should be able to add new features WITHOUT changing existing code.

### ❌ Bad Example - Requires Modification for New Types

```java
/**
 * BAD: Every time we add a new shape, we must MODIFY this class.
 * 
 * If we add Triangle, Pentagon, etc., we keep adding more if-else.
 * This is:
 * - Error-prone (might break existing shapes)
 * - Hard to test (one big method)
 * - Violates OCP (modifying existing code)
 */
public class AreaCalculator {
    
    public double calculateArea(Object shape) {
        
        if (shape instanceof Circle) {
            Circle circle = (Circle) shape;
            return Math.PI * circle.getRadius() * circle.getRadius();
            
        } else if (shape instanceof Rectangle) {
            Rectangle rectangle = (Rectangle) shape;
            return rectangle.getWidth() * rectangle.getHeight();
            
        } else if (shape instanceof Triangle) {
            // Added later - HAD TO MODIFY THIS CLASS!
            Triangle triangle = (Triangle) shape;
            return 0.5 * triangle.getBase() * triangle.getHeight();
        }
        
        // Every new shape = modify this method
        // What if we forget to add a shape? Runtime error!
        
        throw new IllegalArgumentException("Unknown shape");
    }
}
```

### ✅ Good Example - Open for Extension, Closed for Modification

```java
/**
 * GOOD: Define a contract (interface) for all shapes.
 * 
 * Each shape knows how to calculate its own area.
 * The calculator doesn't need to know about specific shapes.
 */
public interface Shape {
    /**
     * Each shape calculates its own area.
     * The implementation is encapsulated within the shape.
     */
    double calculateArea();
}

/**
 * Circle implementation - encapsulates circle-specific logic.
 */
public class Circle implements Shape {
    private double radius;
    
    public Circle(double radius) {
        this.radius = radius;
    }
    
    @Override
    public double calculateArea() {
        return Math.PI * radius * radius;
    }
}

/**
 * Rectangle implementation - encapsulates rectangle-specific logic.
 */
public class Rectangle implements Shape {
    private double width;
    private double height;
    
    public Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }
    
    @Override
    public double calculateArea() {
        return width * height;
    }
}

/**
 * ADDING A NEW SHAPE: Just create a new class!
 * 
 * No modification to AreaCalculator or other shapes.
 * This is EXTENSION without MODIFICATION.
 */
public class Triangle implements Shape {
    private double base;
    private double height;
    
    public Triangle(double base, double height) {
        this.base = base;
        this.height = height;
    }
    
    @Override
    public double calculateArea() {
        return 0.5 * base * height;
    }
}

/**
 * GOOD: Calculator works with ANY shape.
 * 
 * This class is:
 * - CLOSED for modification (never needs to change)
 * - OPEN for extension (works with new shapes automatically)
 */
public class AreaCalculator {
    
    public double calculateTotalArea(List<Shape> shapes) {
        double total = 0;
        for (Shape shape : shapes) {
            total += shape.calculateArea();
        }
        return total;
    }
}
```

### 📝 Real-World Example from Notification System

```java
/**
 * In the Notification System, ChannelHandler follows OCP.
 * 
 * To add WhatsApp support:
 * 1. Create WhatsAppChannelHandler implements ChannelHandler
 * 2. That's it! No changes to existing code.
 */
public interface ChannelHandler {
    ChannelType getChannelType();
    boolean send(Notification notification);
}

// Existing handlers - NEVER MODIFIED when adding new channels
public class EmailChannelHandler implements ChannelHandler { ... }
public class SmsChannelHandler implements ChannelHandler { ... }
public class PushChannelHandler implements ChannelHandler { ... }

// NEW: Just add this class - nothing else changes!
public class WhatsAppChannelHandler implements ChannelHandler {
    @Override
    public ChannelType getChannelType() {
        return ChannelType.WHATSAPP;
    }
    
    @Override
    public boolean send(Notification notification) {
        // Call WhatsApp Business API
        return true;
    }
}
```

### 📝 Interview Tip

> **"OCP means I can add features without touching existing code. In my notification system, adding a new channel like WhatsApp only requires creating a new handler class. The dispatcher and service layer don't change."**

---

## L - Liskov Substitution Principle

> **"Objects of a superclass should be replaceable with objects of a subclass without breaking the application."**

If class B extends class A, you should be able to use B anywhere A is expected.

### ❌ Bad Example - Subclass Breaks Parent Behavior

```java
/**
 * BAD: Square extends Rectangle, but breaks its behavior.
 * 
 * Rectangle says: width and height can be set independently.
 * Square says: width and height must always be equal.
 * 
 * This violates LSP because Square changes the expected behavior.
 */
public class Rectangle {
    protected int width;
    protected int height;
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public int getArea() {
        return width * height;
    }
}

/**
 * BAD: Square overrides setters to maintain square invariant.
 * 
 * This breaks code that expects Rectangle behavior:
 * 
 * Rectangle r = new Square();
 * r.setWidth(5);
 * r.setHeight(10);
 * // Expected area: 5 * 10 = 50
 * // Actual area: 10 * 10 = 100 (because Square changed both!)
 */
public class Square extends Rectangle {
    
    @Override
    public void setWidth(int width) {
        // BAD: Changes BOTH width and height
        this.width = width;
        this.height = width;  // Unexpected side effect!
    }
    
    @Override
    public void setHeight(int height) {
        // BAD: Changes BOTH width and height
        this.width = height;  // Unexpected side effect!
        this.height = height;
    }
}

// This test FAILS with Square but PASSES with Rectangle
public void testRectangle(Rectangle r) {
    r.setWidth(5);
    r.setHeight(10);
    
    // Expects 50, but Square gives 100!
    assert r.getArea() == 50;  // FAILS for Square!
}
```

### ✅ Good Example - Proper Abstraction

```java
/**
 * GOOD: Use a common interface instead of inheritance.
 * 
 * Both Rectangle and Square implement Shape.
 * They don't pretend to be each other.
 */
public interface Shape {
    int getArea();
}

/**
 * Rectangle: width and height can differ.
 */
public class Rectangle implements Shape {
    private final int width;
    private final int height;
    
    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    @Override
    public int getArea() {
        return width * height;
    }
}

/**
 * Square: all sides are equal.
 * 
 * Note: Square doesn't extend Rectangle.
 * They're separate implementations of Shape.
 */
public class Square implements Shape {
    private final int side;
    
    public Square(int side) {
        this.side = side;
    }
    
    @Override
    public int getArea() {
        return side * side;
    }
}

/**
 * GOOD: Code works with any Shape.
 * 
 * Both Rectangle and Square can be used interchangeably
 * because they both correctly implement Shape.
 */
public void printArea(Shape shape) {
    System.out.println("Area: " + shape.getArea());
}
```

### 📝 Interview Tip

> **"LSP means subclasses must honor the parent's contract. A classic violation is Square extending Rectangle. In my projects, I use interfaces like `ChannelHandler` where each implementation (Email, SMS) is truly substitutable."**

---

## I - Interface Segregation Principle

> **"Clients should not be forced to depend on interfaces they don't use."**

Many small, specific interfaces are better than one big, general interface.

### ❌ Bad Example - Fat Interface

```java
/**
 * BAD: One huge interface for all worker types.
 * 
 * Problem: Not all workers do all these things!
 * - A Robot can work but doesn't eat or sleep
 * - A Human does everything
 * 
 * This forces Robot to implement methods it can't use.
 */
public interface Worker {
    void work();
    void eat();       // Robot can't eat!
    void sleep();     // Robot can't sleep!
    void attendMeeting();
    void takeVacation();
}

/**
 * Human implements everything - OK.
 */
public class HumanWorker implements Worker {
    @Override
    public void work() { System.out.println("Working..."); }
    
    @Override
    public void eat() { System.out.println("Eating lunch..."); }
    
    @Override
    public void sleep() { System.out.println("Sleeping..."); }
    
    @Override
    public void attendMeeting() { System.out.println("In meeting..."); }
    
    @Override
    public void takeVacation() { System.out.println("On vacation!"); }
}

/**
 * BAD: Robot is forced to implement methods that don't make sense.
 * 
 * What does a robot do when asked to eat()? 
 * Throw exception? Return nothing? This is messy!
 */
public class RobotWorker implements Worker {
    @Override
    public void work() { System.out.println("Robot working 24/7..."); }
    
    @Override
    public void eat() { 
        // What do we do here?!
        throw new UnsupportedOperationException("Robots don't eat!");
    }
    
    @Override
    public void sleep() {
        // Another useless method
        throw new UnsupportedOperationException("Robots don't sleep!");
    }
    
    @Override
    public void attendMeeting() { System.out.println("Robot in meeting..."); }
    
    @Override
    public void takeVacation() {
        throw new UnsupportedOperationException("Robots don't take vacation!");
    }
}
```

### ✅ Good Example - Segregated Interfaces

```java
/**
 * GOOD: Split into small, focused interfaces.
 * 
 * Each interface represents ONE capability.
 * Classes implement only what they can actually do.
 */

/**
 * Basic work capability - everyone can work.
 */
public interface Workable {
    void work();
}

/**
 * Biological needs - only living things.
 */
public interface Feedable {
    void eat();
    void sleep();
}

/**
 * Meeting capability - optional.
 */
public interface MeetingAttendee {
    void attendMeeting();
}

/**
 * Vacation capability - only humans.
 */
public interface Vacationable {
    void takeVacation();
}

/**
 * GOOD: Human implements all relevant interfaces.
 */
public class HumanWorker implements Workable, Feedable, MeetingAttendee, Vacationable {
    @Override
    public void work() { System.out.println("Working..."); }
    
    @Override
    public void eat() { System.out.println("Eating..."); }
    
    @Override
    public void sleep() { System.out.println("Sleeping..."); }
    
    @Override
    public void attendMeeting() { System.out.println("In meeting..."); }
    
    @Override
    public void takeVacation() { System.out.println("On vacation!"); }
}

/**
 * GOOD: Robot only implements what it can do.
 * 
 * No need to throw UnsupportedOperationException.
 * Clean and honest about its capabilities.
 */
public class RobotWorker implements Workable, MeetingAttendee {
    @Override
    public void work() { System.out.println("Robot working 24/7..."); }
    
    @Override
    public void attendMeeting() { System.out.println("Robot in meeting..."); }
    
    // No eat(), sleep(), or takeVacation() - Robot doesn't implement those interfaces!
}

/**
 * GOOD: Code depends only on the interface it needs.
 */
public class WorkScheduler {
    // Only needs Workable - doesn't care about eating or sleeping
    public void scheduleWork(Workable worker) {
        worker.work();
    }
}

public class LunchScheduler {
    // Only needs Feedable - works with humans, not robots
    public void scheduleLunch(Feedable worker) {
        worker.eat();
    }
}
```

### 📝 Interview Tip

> **"ISP means don't force classes to implement methods they don't need. Instead of one big interface, I create small focused ones. In my projects, I might have `Readable`, `Writable`, `Deletable` instead of one `CRUDRepository` interface."**

---

## D - Dependency Inversion Principle

> **"High-level modules should not depend on low-level modules. Both should depend on abstractions."**

Depend on interfaces, not concrete classes.

### ❌ Bad Example - Direct Dependency on Concrete Class

```java
/**
 * BAD: NotificationService directly depends on concrete EmailSender.
 * 
 * Problems:
 * 1. Can't switch to SMS without changing this class
 * 2. Hard to test (must use real EmailSender)
 * 3. Tight coupling - changes to EmailSender affect this class
 */
public class NotificationService {
    
    // BAD: Depends on concrete class, not abstraction
    private EmailSender emailSender;
    
    public NotificationService() {
        // BAD: Creates its own dependency
        // Can't inject a mock for testing!
        this.emailSender = new EmailSender();
    }
    
    public void sendNotification(String message) {
        // Hardcoded to email - what if we want SMS?
        emailSender.sendEmail("user@example.com", message);
    }
}

public class EmailSender {
    public void sendEmail(String to, String message) {
        // Send via SMTP
    }
}
```

### ✅ Good Example - Depend on Abstraction

```java
/**
 * GOOD: Define an abstraction (interface) for message sending.
 * 
 * Both high-level (NotificationService) and low-level (EmailSender)
 * depend on this abstraction.
 */
public interface MessageSender {
    void send(String recipient, String message);
}

/**
 * Low-level module: Email implementation.
 * 
 * Depends on MessageSender abstraction.
 */
public class EmailSender implements MessageSender {
    @Override
    public void send(String recipient, String message) {
        System.out.println("Sending EMAIL to " + recipient + ": " + message);
    }
}

/**
 * Low-level module: SMS implementation.
 * 
 * Also depends on MessageSender abstraction.
 */
public class SmsSender implements MessageSender {
    @Override
    public void send(String recipient, String message) {
        System.out.println("Sending SMS to " + recipient + ": " + message);
    }
}

/**
 * GOOD: High-level module depends on abstraction, not concrete class.
 * 
 * Benefits:
 * 1. Can switch implementations without changing this class
 * 2. Easy to test - inject mock MessageSender
 * 3. Loose coupling - EmailSender changes don't affect this class
 */
public class NotificationService {
    
    // GOOD: Depends on interface, not concrete class
    private final MessageSender messageSender;
    
    // GOOD: Dependency injected via constructor
    // Spring will automatically inject the right implementation
    public NotificationService(MessageSender messageSender) {
        this.messageSender = messageSender;
    }
    
    public void sendNotification(String recipient, String message) {
        // Works with ANY MessageSender implementation
        messageSender.send(recipient, message);
    }
}

/**
 * GOOD: Easy to test with mock.
 */
public class NotificationServiceTest {
    
    @Test
    void shouldSendNotification() {
        // Create a mock - no real email sent!
        MessageSender mockSender = mock(MessageSender.class);
        
        // Inject mock into service
        NotificationService service = new NotificationService(mockSender);
        
        // Test
        service.sendNotification("user@test.com", "Hello");
        
        // Verify mock was called
        verify(mockSender).send("user@test.com", "Hello");
    }
}

/**
 * Spring Configuration: Wire up the dependency.
 */
@Configuration
public class AppConfig {
    
    @Bean
    public MessageSender messageSender() {
        // Easy to switch: return new SmsSender();
        return new EmailSender();
    }
    
    @Bean
    public NotificationService notificationService(MessageSender sender) {
        return new NotificationService(sender);
    }
}
```

### 📝 Interview Tip

> **"DIP means depend on interfaces, not concrete classes. In my notification system, `NotificationService` depends on `ChannelHandler` interface, not `EmailChannelHandler` directly. This allows me to inject mocks for testing and add new channels without changing the service."**

---

# Design Patterns

Design patterns are reusable solutions to common problems.

---

## Creational Patterns

> Patterns that deal with object creation.

---

### 1. Singleton Pattern

> **Ensure a class has only one instance and provide a global access point.**

**Use when:** You need exactly one instance (database connection pool, configuration, logging).

```java
/**
 * Singleton: Only one instance exists in the entire application.
 * 
 * Use cases:
 * - Database connection pool
 * - Configuration manager
 * - Logger
 * - Cache manager
 */
public class DatabaseConnectionPool {
    
    // Static variable to hold the single instance
    // 'volatile' ensures visibility across threads
    private static volatile DatabaseConnectionPool instance;
    
    // Private constructor prevents instantiation from outside
    private DatabaseConnectionPool() {
        System.out.println("Initializing connection pool...");
        // Initialize connections
    }
    
    /**
     * Double-checked locking for thread-safe lazy initialization.
     * 
     * Why double-checked?
     * 1. First check (without lock): Avoids locking overhead in common case
     * 2. Second check (with lock): Ensures only one instance is created
     */
    public static DatabaseConnectionPool getInstance() {
        // First check (no locking)
        if (instance == null) {
            // Lock only if instance might need creation
            synchronized (DatabaseConnectionPool.class) {
                // Second check (with lock)
                if (instance == null) {
                    instance = new DatabaseConnectionPool();
                }
            }
        }
        return instance;
    }
    
    public Connection getConnection() {
        // Return a connection from the pool
        return null;
    }
}

// Usage
DatabaseConnectionPool pool = DatabaseConnectionPool.getInstance();
Connection conn = pool.getConnection();
```

**Modern Java Alternative (Enum Singleton):**
```java
/**
 * Enum Singleton: Simplest and safest way in Java.
 * 
 * Benefits:
 * - Thread-safe by default
 * - Serialization-safe
 * - Reflection-safe (can't create multiple instances)
 */
public enum ConfigurationManager {
    INSTANCE;
    
    private String databaseUrl;
    private int maxConnections;
    
    public String getDatabaseUrl() {
        return databaseUrl;
    }
    
    public void loadConfiguration() {
        // Load from file or environment
        this.databaseUrl = System.getenv("DATABASE_URL");
        this.maxConnections = 10;
    }
}

// Usage
ConfigurationManager.INSTANCE.loadConfiguration();
String url = ConfigurationManager.INSTANCE.getDatabaseUrl();
```

---

### 2. Factory Method Pattern

> **Define an interface for creating objects, but let subclasses decide which class to instantiate.**

**Use when:** You don't know the exact type of object to create until runtime.

```java
/**
 * Factory Method: Creates objects without specifying the exact class.
 * 
 * Use cases:
 * - Creating different database connections (MySQL, PostgreSQL)
 * - Creating different notification handlers
 * - Creating different payment processors
 */

// Product interface
public interface Notification {
    void send(String message);
}

// Concrete products
public class EmailNotification implements Notification {
    @Override
    public void send(String message) {
        System.out.println("Sending EMAIL: " + message);
    }
}

public class SmsNotification implements Notification {
    @Override
    public void send(String message) {
        System.out.println("Sending SMS: " + message);
    }
}

public class PushNotification implements Notification {
    @Override
    public void send(String message) {
        System.out.println("Sending PUSH: " + message);
    }
}

/**
 * Factory: Creates the right notification based on type.
 * 
 * Client code doesn't need to know about specific classes.
 * Just call factory.create("EMAIL") and use the result.
 */
public class NotificationFactory {
    
    /**
     * Factory method: Creates notification based on type.
     * 
     * Adding a new type? Just add another case here.
     * Client code doesn't change.
     */
    public Notification createNotification(String type) {
        return switch (type.toUpperCase()) {
            case "EMAIL" -> new EmailNotification();
            case "SMS" -> new SmsNotification();
            case "PUSH" -> new PushNotification();
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}

// Usage
NotificationFactory factory = new NotificationFactory();

Notification notification = factory.createNotification("EMAIL");
notification.send("Hello!");  // Sends email

notification = factory.createNotification("SMS");
notification.send("Hello!");  // Sends SMS
```

---

### 3. Builder Pattern

> **Construct complex objects step by step.**

**Use when:** Object has many optional parameters or requires step-by-step construction.

```java
/**
 * Builder Pattern: Constructs complex objects step by step.
 * 
 * Use cases:
 * - Objects with many optional fields (User, Order, Query)
 * - Immutable objects
 * - Objects requiring validation during construction
 * 
 * Benefits:
 * - Readable: user.name("John").email("john@example.com").build()
 * - Flexible: Only set what you need
 * - Immutable: Object is complete after build()
 */
public class User {
    // All fields are final (immutable)
    private final String firstName;      // Required
    private final String lastName;       // Required
    private final String email;          // Optional
    private final String phone;          // Optional
    private final int age;               // Optional
    private final String address;        // Optional
    
    // Private constructor - only Builder can create User
    private User(Builder builder) {
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.email = builder.email;
        this.phone = builder.phone;
        this.age = builder.age;
        this.address = builder.address;
    }
    
    // Getters only (no setters - immutable)
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public int getAge() { return age; }
    public String getAddress() { return address; }
    
    /**
     * Static inner Builder class.
     * 
     * Builder accumulates values then creates the User.
     */
    public static class Builder {
        // Required fields
        private final String firstName;
        private final String lastName;
        
        // Optional fields with defaults
        private String email = "";
        private String phone = "";
        private int age = 0;
        private String address = "";
        
        // Constructor with required fields
        public Builder(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
        
        // Fluent setters for optional fields (return 'this' for chaining)
        public Builder email(String email) {
            this.email = email;
            return this;
        }
        
        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }
        
        public Builder age(int age) {
            this.age = age;
            return this;
        }
        
        public Builder address(String address) {
            this.address = address;
            return this;
        }
        
        /**
         * Build method: Creates the immutable User.
         * 
         * This is where you can add validation.
         */
        public User build() {
            // Validate before creating
            if (age < 0) {
                throw new IllegalStateException("Age cannot be negative");
            }
            return new User(this);
        }
    }
}

// Usage - clean and readable!
User user = new User.Builder("John", "Doe")
    .email("john@example.com")
    .phone("+1234567890")
    .age(30)
    .build();

// Only required fields
User minimalUser = new User.Builder("Jane", "Doe").build();
```

**Lombok Alternative (Less Boilerplate):**
```java
/**
 * With Lombok, you just add an annotation.
 * Lombok generates the builder code at compile time.
 */
@Builder
@Getter
public class Notification {
    private final UUID id;
    private final String userId;
    private final String channel;
    private final String subject;
    private final String content;
    private final String status;
}

// Usage - same as manual builder
Notification notification = Notification.builder()
    .id(UUID.randomUUID())
    .userId("user-123")
    .channel("EMAIL")
    .subject("Welcome")
    .content("Hello!")
    .status("PENDING")
    .build();
```

---

## Structural Patterns

> Patterns that deal with object composition.

---

### 4. Adapter Pattern

> **Convert the interface of a class into another interface clients expect.**

**Use when:** You want to use an existing class, but its interface doesn't match what you need.

```java
/**
 * Adapter Pattern: Makes incompatible interfaces work together.
 * 
 * Real-world analogy: Power adapter for travel.
 * Your laptop has a US plug, but the outlet is European.
 * The adapter converts between them.
 * 
 * Use cases:
 * - Integrating third-party libraries
 * - Working with legacy code
 * - Wrapping external APIs
 */

// Target interface: What our code expects
public interface PaymentProcessor {
    void processPayment(double amount, String currency);
    boolean refund(String transactionId);
}

/**
 * Adaptee: Third-party Stripe SDK with different interface.
 * 
 * We can't modify this - it's from Stripe's library.
 */
public class StripeApi {
    // Stripe has different method names and parameters
    public String createCharge(int amountInCents, String currencyCode) {
        System.out.println("Stripe: Charging " + amountInCents + " cents");
        return "ch_" + System.currentTimeMillis();  // Returns charge ID
    }
    
    public boolean reverseCharge(String chargeId) {
        System.out.println("Stripe: Reversing charge " + chargeId);
        return true;
    }
}

/**
 * Adapter: Converts StripeApi to PaymentProcessor interface.
 * 
 * Our code uses PaymentProcessor.
 * Adapter translates calls to StripeApi.
 */
public class StripeAdapter implements PaymentProcessor {
    
    private final StripeApi stripeApi;
    
    public StripeAdapter(StripeApi stripeApi) {
        this.stripeApi = stripeApi;
    }
    
    @Override
    public void processPayment(double amount, String currency) {
        // Convert dollars to cents (Stripe expects cents)
        int amountInCents = (int) (amount * 100);
        
        // Call Stripe's method
        String chargeId = stripeApi.createCharge(amountInCents, currency);
        System.out.println("Payment processed: " + chargeId);
    }
    
    @Override
    public boolean refund(String transactionId) {
        // Translate our transactionId to Stripe's chargeId format
        return stripeApi.reverseCharge(transactionId);
    }
}

// Usage - code works with PaymentProcessor interface
PaymentProcessor processor = new StripeAdapter(new StripeApi());
processor.processPayment(99.99, "USD");
processor.refund("ch_123456");

// Easy to switch to another provider
// Just create PayPalAdapter, SquareAdapter, etc.
```

---

### 5. Decorator Pattern

> **Attach additional responsibilities to an object dynamically.**

**Use when:** You want to add features to objects without changing their class.

```java
/**
 * Decorator Pattern: Adds behavior without modifying original class.
 * 
 * Real-world analogy: Coffee shop.
 * Base coffee, then add milk, sugar, whipped cream.
 * Each addition "decorates" the coffee.
 * 
 * Use cases:
 * - Adding logging, caching, encryption
 * - Java I/O streams (BufferedInputStream wraps FileInputStream)
 * - Adding features to UI components
 */

// Component interface
public interface DataSource {
    void writeData(String data);
    String readData();
}

// Concrete component: Basic file operations
public class FileDataSource implements DataSource {
    private final String filename;
    
    public FileDataSource(String filename) {
        this.filename = filename;
    }
    
    @Override
    public void writeData(String data) {
        System.out.println("Writing to file: " + data);
        // Write to file
    }
    
    @Override
    public String readData() {
        System.out.println("Reading from file");
        return "file contents";
    }
}

/**
 * Base Decorator: Implements DataSource and wraps another DataSource.
 * 
 * This is the key: Decorator IS-A DataSource and HAS-A DataSource.
 */
public abstract class DataSourceDecorator implements DataSource {
    protected final DataSource wrappee;
    
    public DataSourceDecorator(DataSource source) {
        this.wrappee = source;
    }
    
    @Override
    public void writeData(String data) {
        wrappee.writeData(data);
    }
    
    @Override
    public String readData() {
        return wrappee.readData();
    }
}

/**
 * Concrete Decorator: Adds encryption.
 */
public class EncryptionDecorator extends DataSourceDecorator {
    
    public EncryptionDecorator(DataSource source) {
        super(source);
    }
    
    @Override
    public void writeData(String data) {
        // Encrypt before writing
        String encrypted = encrypt(data);
        System.out.println("Encrypting data...");
        super.writeData(encrypted);
    }
    
    @Override
    public String readData() {
        // Decrypt after reading
        String data = super.readData();
        System.out.println("Decrypting data...");
        return decrypt(data);
    }
    
    private String encrypt(String data) { return "ENCRYPTED[" + data + "]"; }
    private String decrypt(String data) { return data.replace("ENCRYPTED[", "").replace("]", ""); }
}

/**
 * Concrete Decorator: Adds compression.
 */
public class CompressionDecorator extends DataSourceDecorator {
    
    public CompressionDecorator(DataSource source) {
        super(source);
    }
    
    @Override
    public void writeData(String data) {
        String compressed = compress(data);
        System.out.println("Compressing data...");
        super.writeData(compressed);
    }
    
    @Override
    public String readData() {
        String data = super.readData();
        System.out.println("Decompressing data...");
        return decompress(data);
    }
    
    private String compress(String data) { return "COMPRESSED[" + data + "]"; }
    private String decompress(String data) { return data.replace("COMPRESSED[", "").replace("]", ""); }
}

// Usage - stack decorators like layers
DataSource source = new FileDataSource("data.txt");

// Wrap with encryption
source = new EncryptionDecorator(source);

// Wrap with compression (encryption happens first, then compression)
source = new CompressionDecorator(source);

// Write: data → encrypt → compress → file
source.writeData("sensitive data");

// Read: file → decompress → decrypt → data
String data = source.readData();
```

---

## Behavioral Patterns

> Patterns that deal with object communication.

---

### 6. Strategy Pattern

> **Define a family of algorithms, encapsulate each one, and make them interchangeable.**

**Use when:** You want to select an algorithm at runtime.

```java
/**
 * Strategy Pattern: Swap algorithms at runtime.
 * 
 * Real-world analogy: Getting to work.
 * You can drive, take bus, or bike.
 * Same goal (get to work), different strategies.
 * 
 * Use cases:
 * - Different payment methods
 * - Different sorting algorithms
 * - Different validation rules
 * - Different notification channels (EMAIL, SMS, PUSH)
 */

// Strategy interface
public interface PaymentStrategy {
    void pay(double amount);
}

// Concrete strategies
public class CreditCardPayment implements PaymentStrategy {
    private final String cardNumber;
    
    public CreditCardPayment(String cardNumber) {
        this.cardNumber = cardNumber;
    }
    
    @Override
    public void pay(double amount) {
        System.out.println("Paid $" + amount + " with credit card " + 
            cardNumber.substring(cardNumber.length() - 4));
    }
}

public class PayPalPayment implements PaymentStrategy {
    private final String email;
    
    public PayPalPayment(String email) {
        this.email = email;
    }
    
    @Override
    public void pay(double amount) {
        System.out.println("Paid $" + amount + " via PayPal (" + email + ")");
    }
}

public class CryptoPayment implements PaymentStrategy {
    private final String walletAddress;
    
    public CryptoPayment(String walletAddress) {
        this.walletAddress = walletAddress;
    }
    
    @Override
    public void pay(double amount) {
        System.out.println("Paid $" + amount + " in crypto to " + 
            walletAddress.substring(0, 8) + "...");
    }
}

/**
 * Context: Uses a strategy to perform payment.
 * 
 * ShoppingCart doesn't know HOW payment works.
 * It just knows it has a PaymentStrategy.
 */
public class ShoppingCart {
    private final List<Double> items = new ArrayList<>();
    private PaymentStrategy paymentStrategy;
    
    public void addItem(double price) {
        items.add(price);
    }
    
    // Set strategy at runtime
    public void setPaymentStrategy(PaymentStrategy strategy) {
        this.paymentStrategy = strategy;
    }
    
    public void checkout() {
        double total = items.stream().mapToDouble(Double::doubleValue).sum();
        
        // Delegate to strategy
        paymentStrategy.pay(total);
    }
}

// Usage
ShoppingCart cart = new ShoppingCart();
cart.addItem(25.00);
cart.addItem(50.00);

// Pay with credit card
cart.setPaymentStrategy(new CreditCardPayment("1234-5678-9012-3456"));
cart.checkout();  // Paid $75.00 with credit card 3456

// Same cart, different strategy
cart.setPaymentStrategy(new PayPalPayment("user@example.com"));
cart.checkout();  // Paid $75.00 via PayPal (user@example.com)
```

**Real Example from Notification System:**
```java
/**
 * ChannelHandler is a Strategy pattern.
 * 
 * Each handler is a different strategy for sending notifications.
 * The dispatcher selects the right strategy at runtime.
 */
public interface ChannelHandler {
    ChannelType getChannelType();
    boolean send(Notification notification);
}

public class EmailChannelHandler implements ChannelHandler { ... }
public class SmsChannelHandler implements ChannelHandler { ... }
public class PushChannelHandler implements ChannelHandler { ... }

// Dispatcher selects strategy based on channel type
public class ChannelDispatcher {
    private final Map<ChannelType, ChannelHandler> handlers;
    
    public boolean dispatch(Notification notification) {
        ChannelHandler handler = handlers.get(notification.getChannel());
        return handler.send(notification);  // Delegate to strategy
    }
}
```

---

### 7. Observer Pattern

> **Define a one-to-many dependency so that when one object changes state, all dependents are notified.**

**Use when:** You need to notify multiple objects about state changes.

```java
/**
 * Observer Pattern: Notify subscribers when something changes.
 * 
 * Real-world analogy: YouTube subscriptions.
 * You subscribe to a channel.
 * When they upload, you get notified.
 * 
 * Use cases:
 * - Event systems
 * - GUI updates (button click → update UI)
 * - Pub/sub messaging
 * - Stock price updates
 */

// Observer interface
public interface OrderObserver {
    void onOrderStatusChanged(Order order, String newStatus);
}

// Subject (Observable)
public class Order {
    private final String orderId;
    private String status;
    private final List<OrderObserver> observers = new ArrayList<>();
    
    public Order(String orderId) {
        this.orderId = orderId;
        this.status = "CREATED";
    }
    
    // Subscribe
    public void addObserver(OrderObserver observer) {
        observers.add(observer);
    }
    
    // Unsubscribe
    public void removeObserver(OrderObserver observer) {
        observers.remove(observer);
    }
    
    // Notify all observers when status changes
    public void setStatus(String newStatus) {
        this.status = newStatus;
        notifyObservers();
    }
    
    private void notifyObservers() {
        for (OrderObserver observer : observers) {
            observer.onOrderStatusChanged(this, status);
        }
    }
    
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
}

// Concrete observers
public class CustomerNotifier implements OrderObserver {
    @Override
    public void onOrderStatusChanged(Order order, String newStatus) {
        System.out.println("📧 Email to customer: Order " + order.getOrderId() + 
            " is now " + newStatus);
    }
}

public class InventoryManager implements OrderObserver {
    @Override
    public void onOrderStatusChanged(Order order, String newStatus) {
        if ("SHIPPED".equals(newStatus)) {
            System.out.println("📦 Inventory: Reduce stock for order " + order.getOrderId());
        }
    }
}

public class AnalyticsTracker implements OrderObserver {
    @Override
    public void onOrderStatusChanged(Order order, String newStatus) {
        System.out.println("📊 Analytics: Order " + order.getOrderId() + 
            " status changed to " + newStatus);
    }
}

// Usage
Order order = new Order("ORD-123");

// Subscribe observers
order.addObserver(new CustomerNotifier());
order.addObserver(new InventoryManager());
order.addObserver(new AnalyticsTracker());

// Change status → all observers notified
order.setStatus("PROCESSING");
// Output:
// 📧 Email to customer: Order ORD-123 is now PROCESSING
// 📊 Analytics: Order ORD-123 status changed to PROCESSING

order.setStatus("SHIPPED");
// Output:
// 📧 Email to customer: Order ORD-123 is now SHIPPED
// 📦 Inventory: Reduce stock for order ORD-123
// 📊 Analytics: Order ORD-123 status changed to SHIPPED
```

---

### 8. Template Method Pattern

> **Define the skeleton of an algorithm, deferring some steps to subclasses.**

**Use when:** You have an algorithm with fixed steps, but some steps vary.

```java
/**
 * Template Method: Define algorithm skeleton, let subclasses fill in details.
 * 
 * Real-world analogy: Recipe template.
 * All cakes follow: prepare → bake → decorate.
 * But WHAT you prepare and HOW you decorate varies.
 * 
 * Use cases:
 * - Data processing pipelines
 * - Test frameworks (setup → test → teardown)
 * - Document generation (header → content → footer)
 */

/**
 * Abstract class defines the template (skeleton).
 * 
 * Final method: Can't be overridden (enforces the algorithm).
 * Abstract methods: Must be implemented by subclasses.
 * Hook methods: Optional overrides with default behavior.
 */
public abstract class DataProcessor {
    
    /**
     * Template method: Defines the algorithm.
     * 
     * 'final' prevents subclasses from changing the order.
     */
    public final void process() {
        readData();
        processData();
        
        // Hook: Optional step
        if (shouldSaveToDatabase()) {
            saveToDatabase();
        }
        
        generateReport();
    }
    
    // Abstract methods: Subclasses MUST implement
    protected abstract void readData();
    protected abstract void processData();
    
    // Hook method: Subclasses CAN override (has default)
    protected boolean shouldSaveToDatabase() {
        return true;  // Default: yes, save to database
    }
    
    // Concrete method: Same for all subclasses
    protected void saveToDatabase() {
        System.out.println("Saving to database...");
    }
    
    protected void generateReport() {
        System.out.println("Generating report...");
    }
}

/**
 * Concrete implementation: CSV processing.
 */
public class CsvDataProcessor extends DataProcessor {
    
    @Override
    protected void readData() {
        System.out.println("Reading CSV file...");
    }
    
    @Override
    protected void processData() {
        System.out.println("Parsing CSV rows...");
    }
}

/**
 * Concrete implementation: JSON processing.
 */
public class JsonDataProcessor extends DataProcessor {
    
    @Override
    protected void readData() {
        System.out.println("Reading JSON file...");
    }
    
    @Override
    protected void processData() {
        System.out.println("Parsing JSON objects...");
    }
    
    // Override hook: Don't save to database
    @Override
    protected boolean shouldSaveToDatabase() {
        return false;
    }
}

// Usage
DataProcessor csvProcessor = new CsvDataProcessor();
csvProcessor.process();
// Output:
// Reading CSV file...
// Parsing CSV rows...
// Saving to database...
// Generating report...

DataProcessor jsonProcessor = new JsonDataProcessor();
jsonProcessor.process();
// Output:
// Reading JSON file...
// Parsing JSON objects...
// Generating report...
// (No database save - hook returned false)
```

---

# 12-Factor App Principles

The 12-Factor methodology is a set of best practices for building modern, scalable, cloud-native applications.

| Factor | Principle | Example |
|--------|-----------|---------|
| **1. Codebase** | One codebase tracked in Git, many deploys | Same repo for dev, staging, prod |
| **2. Dependencies** | Explicitly declare dependencies | `pom.xml` or `build.gradle` |
| **3. Config** | Store config in environment variables | `DATABASE_URL` env var, not hardcoded |
| **4. Backing Services** | Treat databases, queues as attached resources | PostgreSQL URL from env, can swap easily |
| **5. Build, Release, Run** | Strictly separate build, release, run stages | Maven build → Docker image → Deploy |
| **6. Processes** | Run app as stateless processes | No local session storage, use Redis |
| **7. Port Binding** | Export services via port binding | Spring Boot app on port 8080 |
| **8. Concurrency** | Scale out via process model | Run multiple instances behind load balancer |
| **9. Disposability** | Maximize robustness with fast startup/shutdown | Spring Boot graceful shutdown |
| **10. Dev/Prod Parity** | Keep dev, staging, prod similar | Docker Compose mirrors production |
| **11. Logs** | Treat logs as event streams | Log to stdout, aggregate externally |
| **12. Admin Processes** | Run admin tasks as one-off processes | Database migrations via Flyway |

### Example in Spring Boot

```yaml
# application.yml - Following 12-Factor

spring:
  # Factor 3: Config from environment
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/mydb}
    username: ${DATABASE_USER:postgres}
    password: ${DATABASE_PASSWORD:postgres}
  
  # Factor 4: Backing services as URLs
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}

  kafka:
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}

# Factor 11: Logging to stdout
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

# Factor 7: Port binding
server:
  port: ${PORT:8080}
```

---

# Interview Quick Reference

## SOLID One-Liners

| Principle | One-Liner |
|-----------|-----------|
| **S - Single Responsibility** | One class, one job |
| **O - Open/Closed** | Add features without changing existing code |
| **L - Liskov Substitution** | Subclass can replace parent without breaking |
| **I - Interface Segregation** | Many small interfaces, not one big one |
| **D - Dependency Inversion** | Depend on interfaces, not concrete classes |

## Pattern Cheat Sheet

| Pattern | When to Use | Example |
|---------|-------------|---------|
| **Singleton** | One instance only | Database pool, Config |
| **Factory** | Create objects without specifying class | NotificationFactory |
| **Builder** | Complex object construction | User.builder().name("John").build() |
| **Adapter** | Make incompatible interfaces work | StripeAdapter |
| **Decorator** | Add behavior dynamically | Logging, Caching wrapper |
| **Strategy** | Swap algorithms at runtime | Payment methods, Channel handlers |
| **Observer** | Notify multiple objects | Event listeners, Pub/sub |
| **Template Method** | Fixed algorithm, variable steps | Data processing pipeline |

## Quick Interview Answers

**"What's your favorite design pattern?"**
> "Strategy pattern. I used it in my notification system for channel handlers. Each channel (Email, SMS, Push) implements a common interface. Adding a new channel just means adding a new handler class. The core logic doesn't change."

**"How do you ensure your code follows SOLID?"**
> "I start with small, focused classes (SRP). I use interfaces so I can extend without modifying (OCP). I inject dependencies rather than creating them (DIP). I've seen the difference - well-structured code is so much easier to test and maintain."

**"When would you NOT use a pattern?"**
> "Patterns solve specific problems. If I don't have that problem, using the pattern just adds complexity. For example, Singleton is great for connection pools but terrible if you actually need multiple instances. I always ask: what problem does this solve?"

---

*Good luck with your interviews! 🚀*
