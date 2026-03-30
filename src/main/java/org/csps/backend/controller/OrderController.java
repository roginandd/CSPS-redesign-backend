package org.csps.backend.controller;

import org.csps.backend.domain.dtos.request.OrderPostRequestDTO;
import org.csps.backend.domain.dtos.request.OrderSearchDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.dtos.response.OrderResponseDTO;
import org.csps.backend.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;

    /**
     * Create one or more orders.
     * Student can only create orders for themselves.
     * After creation, add items via /api/order-items endpoint.
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<OrderResponseDTO>> createOrder(
            @AuthenticationPrincipal String studentId,
            @Valid @RequestBody OrderPostRequestDTO orderRequests) {
        OrderResponseDTO responseDTO = orderService.createOrder(studentId, orderRequests);
        return GlobalResponseBuilder.buildResponse("Order created successfully", responseDTO, HttpStatus.CREATED);
    }

    /**
     * Get all orders (admin only).
     * Query params: page (0-indexed), size (default 5), sort (e.g., "orderDate,desc")
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<Page<OrderResponseDTO>>> getAllOrders(
            @PageableDefault(size = 5) Pageable pageable) {
        Page<OrderResponseDTO> responseDTOs = orderService.getAllOrdersPaginated(pageable);
        return GlobalResponseBuilder.buildResponse("Orders retrieved successfully", responseDTOs, HttpStatus.OK);
    }

    /**
     * Get all orders for the authenticated student (paginated by default).
     * Query params: page (0-indexed), size (default 5), sort (e.g., "orderDate,desc")
     * IMPORTANT: This must come BEFORE /{orderId} to avoid route conflicts
     */
    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<Page<OrderResponseDTO>>> getMyOrders(
            @AuthenticationPrincipal String studentId,
            @PageableDefault(size = 5) Pageable pageable) {
        Page<OrderResponseDTO> responseDTOs = orderService.getOrdersByStudentIdPaginated(studentId, pageable);
        return GlobalResponseBuilder.buildResponse("Orders retrieved successfully", responseDTOs, HttpStatus.OK);
    }

    /**
     * Get order by ID.
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<OrderResponseDTO>> getOrderById(
            @PathVariable Long orderId,
            Authentication authentication) {
        OrderResponseDTO responseDTO = orderService.getOrderById(orderId, resolveStudentScope(authentication));
        return GlobalResponseBuilder.buildResponse("Order retrieved successfully", responseDTO, HttpStatus.OK);
    }

    /**
     * Get all orders by data
     * 
     * @param pageable
     * @return
     */
    @GetMapping("/sorted-by-date")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<Page<OrderResponseDTO>>> getAllOrdersSortedByDate(
            @PageableDefault(size = 3) Pageable pageable) {
        Page<OrderResponseDTO> responseDTOs = orderService.getAllOrdersPaginatedSortByDate(pageable);
        return GlobalResponseBuilder.buildResponse("Orders retrieved successfully", responseDTOs, HttpStatus.OK);
    }

    /**
     * Search and filter orders (admin only).
     * Advanced filtering by student name/ID, order status, and date range.
     * Query params: studentName, studentId, status, startDate, endDate, 
     *               page (0-indexed), size (default 20), sort (e.g., "orderDate,desc")
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<Page<OrderResponseDTO>>> searchOrders(
            @ModelAttribute OrderSearchDTO searchDTO,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponseDTO> results = orderService.searchOrders(searchDTO, pageable, resolveStudentScope(authentication));
        return GlobalResponseBuilder.buildResponse("Orders retrieved successfully", results, HttpStatus.OK);
    }

    /**
     * Delete order.
     * Only admins can delete orders.
     */
    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<Void>> deleteOrder(
            @PathVariable Long orderId) {
        orderService.deleteOrder(orderId);
        return GlobalResponseBuilder.buildResponse("Order deleted successfully", null, HttpStatus.NO_CONTENT);
    }

    /**
     * Cancel a pending order owned by the authenticated student.
     */
    @PatchMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<OrderResponseDTO>> cancelOrder(
            @AuthenticationPrincipal String studentId,
            @PathVariable Long orderId) {
        OrderResponseDTO responseDTO = orderService.cancelOrder(studentId, orderId);
        return GlobalResponseBuilder.buildResponse("Order cancelled successfully", responseDTO, HttpStatus.OK);
    }

    private String resolveStudentScope(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        boolean studentRequest = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STUDENT".equals(authority.getAuthority()));

        return studentRequest ? String.valueOf(authentication.getPrincipal()) : null;
    }
}
