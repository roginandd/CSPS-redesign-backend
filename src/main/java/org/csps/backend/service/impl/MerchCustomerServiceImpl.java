package org.csps.backend.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.csps.backend.domain.dtos.request.BulkMerchPaymentRequestDTO;
import org.csps.backend.domain.dtos.request.BulkPaymentEntryDTO;
import org.csps.backend.domain.dtos.response.MerchCustomerResponseDTO;
import org.csps.backend.domain.dtos.response.OrderResponseDTO;
import org.csps.backend.domain.entities.MerchVariantItem;
import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.entities.OrderItem;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.MerchNotFoundException;
import org.csps.backend.exception.StudentNotFoundException;
import org.csps.backend.mapper.OrderMapper;
import org.csps.backend.repository.CartItemRepository;
import org.csps.backend.repository.MerchRepository;
import org.csps.backend.repository.MerchVariantItemRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.OrderRepository;
import org.csps.backend.repository.StudentRepository;
import org.csps.backend.service.MerchCustomerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Service implementation for merch-customer relationship operations.
 * Handles admin-facing queries: customer lookup, bulk payment recording,
 * and membership eligibility checks.
 */
@Service
@RequiredArgsConstructor
public class MerchCustomerServiceImpl implements MerchCustomerService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final StudentRepository studentRepository;
    private final MerchVariantItemRepository merchVariantItemRepository;
    private final MerchRepository merchRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderMapper orderMapper;

    /**
     * Retrieves a paginated list of customers (order items) who purchased a specific merch.
     * Uses EntityGraph on the repository to eagerly load student profile, order, and merch details.
     *
     * @param merchId  the merch ID to look up customers for
     * @param pageable pagination details (page, size, sort)
     * @return paginated MerchCustomerResponseDTO with student and order details
     */
    @Override
    public Page<MerchCustomerResponseDTO> getCustomersByMerchId(Long merchId, Pageable pageable) {
        validateMerchExists(merchId);
        Page<OrderItem> orderItems = orderItemRepository.findByMerchId(merchId, pageable);
        return orderItems.map(this::mapToMerchCustomerDTO);
    }

    /**
     * Retrieves a paginated list of customers for a specific merch, filtered by order status.
     * Useful for admins who want to see only PENDING, CLAIMED, etc. orders for a merch.
     *
     * @param merchId  the merch ID to look up
     * @param status   the order status filter
     * @param pageable pagination details
     * @return filtered paginated MerchCustomerResponseDTO
     */
    @Override
    public Page<MerchCustomerResponseDTO> getCustomersByMerchIdAndStatus(Long merchId, OrderStatus status, Pageable pageable) {
        validateMerchExists(merchId);
        Page<OrderItem> orderItems = orderItemRepository.findByMerchIdAndOrderStatus(merchId, status, pageable);
        return orderItems.map(this::mapToMerchCustomerDTO);
    }

    /**
     * Returns total count of order items for a specific merch.
     * Lightweight alternative to a full page request.
     *
     * @param merchId the merch ID to count for
     * @return total number of order items
     */
    @Override
    public long getCustomerCountByMerchId(Long merchId) {
        validateMerchExists(merchId);
        return orderItemRepository.countByMerchId(merchId);
    }

    /**
     * Batch-creates orders for multiple students who already paid for a specific merch.
     * Each entry contains a studentId and the actual orderDate (preserving real purchase dates).
     * Uses a batch-save approach: all Order and OrderItem entities are collected
     * into temporary lists, then persisted in two bulk saveAll() calls instead
     * of N+1 individual saves. Stock is decremented once at the end.
     *
     * @param requestDTO contains entries (studentId+orderDate pairs), merchVariantItemId, and quantity
     * @return list of created OrderResponseDTOs (one per student)
     * @throws StudentNotFoundException if any student ID is invalid
     * @throws MerchNotFoundException   if the MerchVariantItem ID is invalid
     * @throws InvalidRequestException  if stock is insufficient
     */
    @Override
    @Transactional
    public List<OrderResponseDTO> recordBulkMerchPayment(BulkMerchPaymentRequestDTO requestDTO) {
        if (requestDTO == null) {
            throw new InvalidRequestException("Bulk merch payment request is required");
        }

        if (requestDTO.getEntries() == null || requestDTO.getEntries().isEmpty()) {
            throw new InvalidRequestException("At least one bulk payment entry is required");
        }

        /* validate MerchVariantItem exists with pessimistic write lock to prevent concurrent updates */
        MerchVariantItem merchVariantItem = merchVariantItemRepository.findByIdWithLock(requestDTO.getMerchVariantItemId())
                .orElseThrow(() -> new MerchNotFoundException(
                        "MerchVariantItem not found with ID: " + requestDTO.getMerchVariantItemId()));

        int quantityPerStudent = requestDTO.getQuantity() != null ? requestDTO.getQuantity() : 1;
        if (quantityPerStudent <= 0) {
            throw new InvalidRequestException("Quantity must be greater than 0");
        }

        MerchType merchType = merchVariantItem.getMerchVariant().getMerch().getMerchType();
        if (merchType == MerchType.MEMBERSHIP) {
            throw new InvalidRequestException("Bulk merch payment does not support membership items. Use the membership payment flow.");
        }

        double pricePerItem = merchVariantItem.getPrice();

        /* validate total stock upfront before any persistence */
        int totalStockNeeded = quantityPerStudent * requestDTO.getEntries().size();
        if (merchVariantItem.getStockQuantity() < totalStockNeeded) {
            throw new InvalidRequestException(
                    "Insufficient stock. Available: " + merchVariantItem.getStockQuantity()
                            + ", Required: " + totalStockNeeded);
        }

        try {
            /* PHASE 1: Build all Order entities using each entry's actual orderDate */
            List<Order> ordersToSave = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (BulkPaymentEntryDTO entry : requestDTO.getEntries()) {
                Student student = studentRepository.findByStudentId(entry.getStudentId())
                        .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + entry.getStudentId()));

                Order order = Order.builder()
                        .student(student)
                        .orderDate(entry.getOrderDate())
                        .totalPrice(pricePerItem * quantityPerStudent)
                        .updatedAt(now)
                        .orderStatus(OrderStatus.TO_BE_CLAIMED)
                        .quantity(quantityPerStudent)
                        .build();

                ordersToSave.add(order);
            }

            /* PHASE 2: Batch-save all Orders in one round-trip */
            List<Order> savedOrders = orderRepository.saveAll(ordersToSave);

            /* PHASE 3: Build all OrderItem entities referencing their saved Orders */
            List<OrderItem> orderItemsToSave = new ArrayList<>();

            for (Order savedOrder : savedOrders) {
                OrderItem orderItem = OrderItem.builder()
                        .order(savedOrder)
                        .merchVariantItem(merchVariantItem)
                        .quantity(quantityPerStudent)
                        .priceAtPurchase(pricePerItem)
                        .orderStatus(OrderStatus.TO_BE_CLAIMED)
                        .build();

                orderItemsToSave.add(orderItem);
            }

            /* PHASE 4: Batch-save all OrderItems in one round-trip */
            orderItemRepository.saveAll(orderItemsToSave);

            /* PHASE 5: Decrement stock once and persist (pessimistic lock already held) */
            merchVariantItem.setStockQuantity(merchVariantItem.getStockQuantity() - totalStockNeeded);
            merchVariantItemRepository.save(merchVariantItem);

            /* Map saved orders to response DTOs */
            return savedOrders.stream()
                    .map(orderMapper::toResponseDTO)
                    .toList();
        } catch (Exception e) {
            /* log error and rethrow to trigger transaction rollback */
            System.err.println("Error recording bulk merch payment: " + e.getMessage());
            e.printStackTrace();
            throw new InvalidRequestException("Failed to record bulk merch payment: " + e.getMessage());
        }
    }

    /**
     * Checks whether a student has a MEMBERSHIP merch type in their cart
     * OR in any PENDING order. This combined check is used to determine
     * whether to hide MEMBERSHIP merch from the store listing.
     *
     * @param studentId the student ID to check
     * @return true if membership is already in cart or pending order
     */
    @Override
    public boolean hasMembershipInCartOrPendingOrder(String studentId) {
        boolean inCart = cartItemRepository.existsByStudentIdAndMerchType(studentId, MerchType.MEMBERSHIP);
        boolean inPendingOrder = orderItemRepository.existsByStudentIdAndMerchTypeAndStatus(
                studentId, MerchType.MEMBERSHIP, OrderStatus.PENDING);
        return inCart || inPendingOrder;
    }

    /**
     * Retrieves ALL customers (order items) for a specific merch without pagination.
     * Useful for exporting the full customer list to CSV.
     *
     * @param merchId the merch ID to look up customers for
     * @return complete list of MerchCustomerResponseDTO
     */
    @Override
    public List<MerchCustomerResponseDTO> getAllCustomersByMerchId(Long merchId) {
        validateMerchExists(merchId);
        List<OrderItem> orderItems = orderItemRepository.findAllByMerchId(merchId);
        return orderItems.stream()
                .map(this::mapToMerchCustomerDTO)
                .toList();
    }

    /**
     * Maps an OrderItem entity to a MerchCustomerResponseDTO.
     * Extracts student profile data, merch variant details, and order info
     * into a flat DTO suitable for admin views.
     *
     * @param orderItem the order item entity to map
     * @return populated MerchCustomerResponseDTO
     */
    private MerchCustomerResponseDTO mapToMerchCustomerDTO(OrderItem orderItem) {
        var order = orderItem.getOrder();
        var student = order.getStudent();
        var userProfile = student.getUserAccount().getUserProfile();
        var merchVariantItem = orderItem.getMerchVariantItem();
        var merchVariant = merchVariantItem.getMerchVariant();
        var merch = merchVariant.getMerch();

        String studentName = (userProfile.getFirstName() != null ? userProfile.getFirstName() : "")
                + " " + (userProfile.getLastName() != null ? userProfile.getLastName() : "");

        return MerchCustomerResponseDTO.builder()
                .studentId(student.getStudentId())
                .studentName(studentName.trim())
                .yearLevel(student.getYearLevel())
                .merchName(merch.getMerchName())
                .color(merchVariant.getColor())
                .design(merchVariant.getDesign())
                .size(merchVariantItem.getSize())
                .quantity(orderItem.getQuantity())
                .totalPrice(orderItem.getPriceAtPurchase() * orderItem.getQuantity())
                .orderStatus(orderItem.getOrderStatus())
                .orderDate(order.getOrderDate())
                .s3ImageKey(merchVariant.getS3ImageKey())
                .build();
    }

    /**
     * Validates that a Merch entity exists by its ID.
     * Throws MerchNotFoundException if the merch doesn't exist.
     *
     * @param merchId the merch ID to validate
     */
    private void validateMerchExists(Long merchId) {
        if (!merchRepository.existsById(merchId)) {
            throw new MerchNotFoundException("Merch not found with ID: " + merchId);
        }
    }
}
