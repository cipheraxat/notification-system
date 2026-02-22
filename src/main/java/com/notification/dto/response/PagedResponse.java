package com.notification.dto.response;

// =====================================================
// PagedResponse.java - Paginated Response Wrapper
// =====================================================
//
// When returning lists that could be large (like a user's
// notification inbox), we use pagination.
//
// Instead of returning ALL notifications at once:
// - Page 1: notifications 1-20
// - Page 2: notifications 21-40
// - etc.
//
// This is more efficient and provides better UX.
//

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Paginated response wrapper.
 * 
 * Example JSON response:
 * {
 *   "content": [
 *     { ... notification 1 ... },
 *     { ... notification 2 ... }
 *   ],
 *   "pageNumber": 0,
 *   "pageSize": 20,
 *   "totalElements": 150,
 *   "totalPages": 8,
 *   "first": true,
 *   "last": false,
 *   "hasNext": true,
 *   "hasPrevious": false
 * }
 * 
 * @param <T> The type of elements in the list
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

    /**
     * The actual list of items on this page.
     */
    private List<T> content;
    
    /**
     * Current page number (0-indexed).
     * Page 0 = first page.
     */
    private int pageNumber;
    
    /**
     * Number of items per page.
     */
    private int pageSize;
    
    /**
     * Total number of items across all pages.
     */
    private long totalElements;
    
    /**
     * Total number of pages.
     */
    private int totalPages;
    
    /**
     * Is this the first page?
     */
    private boolean first;
    
    /**
     * Is this the last page?
     */
    private boolean last;
    
    /**
     * Is there a next page?
     */
    private boolean hasNext;
    
    /**
     * Is there a previous page?
     */
    private boolean hasPrevious;
    
    // ==================== Factory Methods ====================
    
    /**
     * Create a PagedResponse from a Spring Data Page.
     * 
     * Spring Data's Page object has all the pagination info.
     * This method converts it to our PagedResponse DTO.
     * 
     * @param page The Spring Data Page object
     * @return A PagedResponse with the same content
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        return PagedResponse.<T>builder()
            .content(page.getContent())
            .pageNumber(page.getNumber())
            .pageSize(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    /**
     * Create a PagedResponse with mapped content.
     * 
     * This is useful when you have a Page<Entity> but want
     * to return a PagedResponse<DTO>.
     * 
     * Usage:
     *   Page<Notification> entities = repository.findAll(pageable);
     *   PagedResponse<NotificationResponse> dtos = PagedResponse.from(
     *       entities, 
     *       NotificationResponse::from  // Method reference for conversion
     *   );
     * 
     * @param page The Spring Data Page of entities
     * @param mapper Function to convert Entity to DTO
     * @return A PagedResponse with converted content
     */
    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        // Convert each entity to DTO using the mapper function
        List<T> mappedContent = page.getContent()
            .stream()
            .map(mapper)
            .collect(Collectors.toList());
        
        return PagedResponse.<T>builder()
            .content(mappedContent)
            .pageNumber(page.getNumber())
            .pageSize(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    /**
     * Create an empty PagedResponse.
     * 
     * This is useful when there are no results to return,
     * or when certain filters are not yet implemented.
     * 
     * @return An empty PagedResponse with no content
     */
    public static <T> PagedResponse<T> empty() {
        return PagedResponse.<T>builder()
            .content(Collections.emptyList())
            .pageNumber(0)
            .pageSize(0)
            .totalElements(0)
            .totalPages(0)
            .first(true)
            .last(true)
            .hasNext(false)
            .hasPrevious(false)
            .build();
    }
}
