package com.notification.controller;

// =====================================================
// TemplateController.java - Template Management API
// =====================================================
//
// CRUD endpoints for notification templates.
//

import com.notification.dto.request.CreateTemplateRequest;
import com.notification.dto.response.ApiResponse;
import com.notification.dto.response.TemplateResponse;
import com.notification.model.enums.ChannelType;
import com.notification.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for template management.
 */
@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Templates", description = "Template management APIs")
public class TemplateController {

    private final TemplateService templateService;
    
    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }
    
    // ==================== Create Template ====================
    
    /**
     * Create a new notification template.
     */
    @PostMapping
    @Operation(
        summary = "Create a template",
        description = "Create a new notification template with placeholders"
    )
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {
        
        TemplateResponse response = templateService.createTemplate(request);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Template created successfully", response));
    }
    
    // ==================== Get Templates ====================
    
    /**
     * Get all templates, optionally filtered by channel.
     */
    @GetMapping
    @Operation(
        summary = "Get all templates",
        description = "Get all active templates, optionally filtered by channel"
    )
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> getAllTemplates(
            @Parameter(description = "Filter by channel (optional)")
            @RequestParam(required = false) ChannelType channel) {
        
        List<TemplateResponse> templates = templateService.getAllTemplates(channel);
        
        return ResponseEntity.ok(ApiResponse.success("Templates retrieved", templates));
    }
    
    /**
     * Get a template by ID.
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get template by ID",
        description = "Get a specific template by its ID"
    )
    public ResponseEntity<ApiResponse<TemplateResponse>> getTemplateById(@PathVariable UUID id) {
        TemplateResponse response = templateService.getTemplateById(id);
        return ResponseEntity.ok(ApiResponse.success("Template retrieved", response));
    }
    
    /**
     * Get a template by name.
     */
    @GetMapping("/name/{name}")
    @Operation(
        summary = "Get template by name",
        description = "Get a specific template by its unique name"
    )
    public ResponseEntity<ApiResponse<TemplateResponse>> getTemplateByName(@PathVariable String name) {
        TemplateResponse response = templateService.getTemplateByName(name);
        return ResponseEntity.ok(ApiResponse.success("Template retrieved", response));
    }
    
    // ==================== Update Template ====================
    
    /**
     * Update a template.
     * 
     * @PutMapping is for full resource replacement.
     * The entire template is replaced with the new data.
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "Update a template",
        description = "Update an existing template"
    )
    public ResponseEntity<ApiResponse<TemplateResponse>> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTemplateRequest request) {
        
        TemplateResponse response = templateService.updateTemplate(id, request);
        
        return ResponseEntity.ok(ApiResponse.success("Template updated successfully", response));
    }
    
    // ==================== Delete Template ====================
    
    /**
     * Delete (deactivate) a template.
     * 
     * @DeleteMapping handles HTTP DELETE requests.
     * 
     * Note: This is a soft delete - the template is marked as inactive
     * but not removed from the database.
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a template",
        description = "Soft delete (deactivate) a template"
    )
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable UUID id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponse.success("Template deleted successfully"));
    }
}
