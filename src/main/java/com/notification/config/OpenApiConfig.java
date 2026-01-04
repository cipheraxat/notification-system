package com.notification.config;

// =====================================================
// OpenApiConfig.java - Swagger/OpenAPI Configuration
// =====================================================
//
// This configures the OpenAPI (Swagger) documentation.
// Once the app is running, you can access:
// - http://localhost:8080/swagger-ui.html - Interactive UI
// - http://localhost:8080/v3/api-docs - JSON specification
//

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for OpenAPI/Swagger documentation.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;
    
    /**
     * Configure the OpenAPI specification.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            // API Information
            .info(new Info()
                .title("Notification System API")
                .version("1.0.0")
                .description("""
                    A multi-channel notification system supporting:
                    - **Email** - Send emails via SMTP or email services
                    - **SMS** - Send text messages via Twilio/Nexmo
                    - **Push** - Send mobile push notifications via FCM/APNs
                    - **In-App** - Store notifications for in-app inbox
                    
                    ## Features
                    - Rate limiting per user and channel
                    - Template-based messaging
                    - Retry with exponential backoff
                    - Priority-based processing
                    
                    ## Authentication
                    This demo doesn't include authentication. In production,
                    you would add OAuth2 or JWT authentication.
                    """)
                .contact(new Contact()
                    .name("Your Name")
                    .email("your.email@example.com")
                    .url("https://github.com/yourusername"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            
            // Server configuration
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Local development server")
            ));
    }
}
