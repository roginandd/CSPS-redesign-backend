package org.csps.backend.controller;

import java.util.List;
import java.util.Map;

import org.csps.backend.domain.dtos.request.BulkMerchPaymentRequestDTO;
import org.csps.backend.domain.dtos.response.MerchCustomerResponseDTO;
import org.csps.backend.domain.dtos.response.OrderResponseDTO;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.service.MerchCustomerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for merch-customer relationship operations.
 * All endpoints are admin-only and provide functionality for:
 * - Viewing customers who purchased specific merchandise
 * - Batch-recording payments for students
 * - Checking membership eligibility status
 */
@RestController
@RequestMapping("/api/merch-customers")
@RequiredArgsConstructor
public class MerchCustomerController {

    private final MerchCustomerService merchCustomerService;

    /**
     * Get a paginated list of customers who purchased a specific merch.
     * Eagerly loads student profile and order/merch details via EntityGraph.
     *
     * @param merchId the merch ID to look up customers for
     * @param page    zero-based page index (default 0)
     * @param size    number of items per page (default 7)
     * @return paginated list of MerchCustomerResponseDTO
     */
    @GetMapping("/{merchId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<MerchCustomerResponseDTO>> getCustomersByMerchId(
            @PathVariable Long merchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size,
            @RequestParam(defaultValue = "true") boolean includeFreebies
            ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderItemId"));
        Page<MerchCustomerResponseDTO> customers = merchCustomerService.getCustomersByMerchId(merchId, pageable, includeFreebies);
        return ResponseEntity.ok(customers);
    }

    /**
     * Get a paginated list of customers who purchased a specific merch,
     * filtered by order status (e.g., PENDING, TO_BE_CLAIMED, CLAIMED, REJECTED).
     *
     * @param merchId the merch ID to look up
     * @param status  the order status to filter by
     * @param page    zero-based page index (default 0)
     * @param size    number of items per page (default 7)
     * @return filtered paginated list of MerchCustomerResponseDTO
     */
    @GetMapping("/{merchId}/by-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<MerchCustomerResponseDTO>> getCustomersByMerchIdAndStatus(
            @PathVariable Long merchId,
            @RequestParam OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size,
            @RequestParam(defaultValue = "true") boolean includeFreebies) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderItemId"));
        Page<MerchCustomerResponseDTO> customers = merchCustomerService.getCustomersByMerchIdAndStatus(merchId, status, pageable, includeFreebies);
        return ResponseEntity.ok(customers);
    }

    /**
     * Get the total count of customers (order items) for a specific merch.
     * Lightweight endpoint — returns only a count, no full data fetch.
     *
     * @param merchId the merch ID to count for
     * @return JSON object with "count" key
     */
    @GetMapping("/{merchId}/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getCustomerCountByMerchId(
            @PathVariable Long merchId,
            @RequestParam(defaultValue = "true") boolean includeFreebies) {
        long count = merchCustomerService.getCustomerCountByMerchId(merchId, includeFreebies);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Batch-create orders for a list of students who already paid for a specific merch.
     * Each entry includes the studentId and the actual orderDate (preserving real timestamps).
     * Creates one Order per entry, each with a single OrderItem referencing
     * the specified MerchVariantItem (SKU). Decrements stock accordingly.
     *
     * @param requestDTO contains entries (studentId+orderDate pairs), merchVariantItemId, and quantity
     * @return list of created OrderResponseDTOs (one per student)
     */
    @PostMapping("/bulk-payment")
    @PreAuthorize("hasRole('ADMIN_FINANCE')")
    public ResponseEntity<List<OrderResponseDTO>> recordBulkMerchPayment(
            @Valid @RequestBody BulkMerchPaymentRequestDTO requestDTO) {
        List<OrderResponseDTO> createdOrders = merchCustomerService.recordBulkMerchPayment(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrders);
    }

    /**
     * Check if a student has a MEMBERSHIP merch type in their cart or pending order.
     * Used by admin to verify membership eligibility before taking action.
     *
     * @param studentId the student ID to check
     * @return JSON object with "hasMembership" boolean
     */
    @GetMapping("/membership-status/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> checkMembershipStatus(@PathVariable String studentId) {
        boolean hasMembership = merchCustomerService.hasMembershipInCartOrPendingOrder(studentId);
        return ResponseEntity.ok(Map.of("hasMembership", hasMembership));
    }

    /**
     * Get ALL customers for a specific merch (unpaginated) for CSV export.
     * Returns the full list without pagination — use for exporting to CSV.
     *
     * @param merchId the merch ID to export customers for
     * @return complete list of MerchCustomerResponseDTO
     */
    @GetMapping("/{merchId}/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MerchCustomerResponseDTO>> exportCustomersByMerchId(
            @PathVariable Long merchId,
            @RequestParam(defaultValue = "true") boolean includeFreebies) {
        List<MerchCustomerResponseDTO> customers = merchCustomerService.getAllCustomersByMerchId(merchId, includeFreebies);
        return ResponseEntity.ok(customers);
    }
}
