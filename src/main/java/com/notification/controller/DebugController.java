package com.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controller exposing simple debug endpoints used during stress tests.
 *
 * - /api/v1/debug/alloc  allocate a chunk of memory on the JVM heap
 * - /api/v1/debug/clear  release previously allocated memory
 *
 * These endpoints are NOT intended for production use and should be
 * guarded or removed in a real deployment.
 */
@RestController
@RequestMapping("/api/v1/debug")
@Tag(name = "Debug", description = "Helper endpoints for load testing and diagnostics")
public class DebugController {

    // hold onto allocated byte arrays so they remain on heap
    private final AtomicReference<List<byte[]>> heapHolder = new AtomicReference<>(new ArrayList<>());

    /**
     * Allocate the specified amount of memory in megabytes.
     *
     * Example: POST /api/v1/debug/alloc?mb=50
     */
    @PostMapping("/alloc")
    @Operation(summary = "Allocate heap space", description = "Allocates the given number of megabytes on the JVM heap")
    public ResponseEntity<String> allocate(@RequestParam(defaultValue = "10") int mb) {
        // create byte arrays of 1MB each
        List<byte[]> list = heapHolder.get();
        for (int i = 0; i < mb; i++) {
            list.add(new byte[1024 * 1024]);
        }
        heapHolder.set(list);
        return ResponseEntity.ok("Allocated " + mb + " MB of heap");
    }

    /**
     * Clear all allocations previously made with /alloc.
     */
    @PostMapping("/clear")
    @Operation(summary = "Clear heap allocations", description = "Releases all memory previously allocated via /alloc")
    public ResponseEntity<String> clear() {
        heapHolder.set(new ArrayList<>());
        System.gc(); // hint GC to reclaim
        return ResponseEntity.ok("Heap cleared");
    }
}
