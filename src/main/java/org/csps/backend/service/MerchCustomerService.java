package org.csps.backend.service;

import org.csps.backend.domain.dtos.request.BulkMerchPaymentRequestDTO;
import org.csps.backend.domain.dtos.response.MerchCustomerResponseDTO;
import org.csps.backend.domain.dtos.response.OrderResponseDTO;
import org.csps.backend.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service for merch-customer relationship operations.
 * Provides admin-facing functionality: viewing customers who purchased specific merch,
 * batch-recording payments, and checking membership eligibility.
 */
public interface MerchCustomerService {

    /**
     * Get a paginated list of customers who purchased a specific merch.
     * Eagerly loads student profile and order details to prevent N+1 queries.
     *
     * @param merchId  the merch ID to look up customers for
     * @param pageable pagination details
     * @return paginated list of MerchCustomerResponseDTO
     */
    Page<MerchCustomerResponseDTO> getCustomersByMerchId(Long merchId, Pageable pageable, boolean includeFreebies);

    /**
     * Get a paginated list of customers who purchased a specific merch,
     * filtered by order status (e.g., only CLAIMED or PENDING orders).
     *
     * @param merchId  the merch ID to look up customers for
     * @param status   the order status filter
     * @param pageable pagination details
     * @return paginated list of MerchCustomerResponseDTO matching the status
     */
    Page<MerchCustomerResponseDTO> getCustomersByMerchIdAndStatus(Long merchId, OrderStatus status, Pageable pageable, boolean includeFreebies);

    /**
     * Get the total count of customers (order items) for a specific merch.
     * Lightweight alternative to loading full page data.
     *
     * @param merchId the merch ID to count for
     * @return total number of order items for this merch
     */
    long getCustomerCountByMerchId(Long merchId, boolean includeFreebies);

    /**
     * Batch-create orders for a list of students who already paid for a specific merch.
     * Creates one Order per student, each with a single OrderItem pointing to the
     * specified MerchVariantItem (SKU). Decrements stock for each order created.
     *
     * @param requestDTO contains studentIds, merchVariantItemId, and quantity
     * @return list of created OrderResponseDTOs (one per student)
     */
    List<OrderResponseDTO> recordBulkMerchPayment(BulkMerchPaymentRequestDTO requestDTO);

    /**
     * Check if a student has a MEMBERSHIP merch type in their cart
     * OR in any PENDING order. Used to determine whether to hide
     * MEMBERSHIP merch from the store listing.
     *
     * @param studentId the student ID to check
     * @return true if membership is already in cart or pending order
     */
    boolean hasMembershipInCartOrPendingOrder(String studentId);

    /**
     * Get ALL customers for a specific merch (unpaginated).
     * Used for CSV export of full customer lists.
     *
     * @param merchId the merch ID to look up customers for
     * @return complete list of MerchCustomerResponseDTO
     */
    List<MerchCustomerResponseDTO> getAllCustomersByMerchId(Long merchId, boolean includeFreebies);
}
