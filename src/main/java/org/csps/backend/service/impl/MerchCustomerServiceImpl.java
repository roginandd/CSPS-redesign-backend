package org.csps.backend.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.csps.backend.domain.dtos.request.BulkMerchPaymentRequestDTO;
import org.csps.backend.domain.dtos.request.BulkPaymentEntryDTO;
import org.csps.backend.domain.dtos.response.MerchCustomerResponseDTO;
import org.csps.backend.domain.dtos.response.OrderResponseDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieAssignmentResponseDTO;
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
import org.csps.backend.service.TicketFreebieAssignmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

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
    private final TicketFreebieAssignmentService ticketFreebieAssignmentService;

    @Override
    public Page<MerchCustomerResponseDTO> getCustomersByMerchId(Long merchId, Pageable pageable, boolean includeFreebies) {
        validateMerchExists(merchId);
        Page<OrderItem> orderItems = orderItemRepository.findByMerchId(merchId, pageable);
        Map<Long, List<TicketFreebieAssignmentResponseDTO>> assignmentsByOrderItemId =
                loadAssignments(orderItems.getContent(), includeFreebies);
        return orderItems.map(orderItem -> mapToMerchCustomerDTO(
                orderItem,
                assignmentsByOrderItemId.get(orderItem.getOrderItemId()),
                includeFreebies));
    }

    @Override
    public Page<MerchCustomerResponseDTO> getCustomersByMerchIdAndStatus(Long merchId, OrderStatus status, Pageable pageable, boolean includeFreebies) {
        validateMerchExists(merchId);
        Page<OrderItem> orderItems = orderItemRepository.findByMerchIdAndOrderStatus(merchId, status, pageable);
        Map<Long, List<TicketFreebieAssignmentResponseDTO>> assignmentsByOrderItemId =
                loadAssignments(orderItems.getContent(), includeFreebies);
        return orderItems.map(orderItem -> mapToMerchCustomerDTO(
                orderItem,
                assignmentsByOrderItemId.get(orderItem.getOrderItemId()),
                includeFreebies));
    }

    @Override
    public long getCustomerCountByMerchId(Long merchId, boolean includeFreebies) {
        validateMerchExists(merchId);
        return orderItemRepository.countByMerchId(merchId);
    }

    @Override
    @Transactional
    public List<OrderResponseDTO> recordBulkMerchPayment(BulkMerchPaymentRequestDTO requestDTO) {
        if (requestDTO == null) {
            throw new InvalidRequestException("Bulk merch payment request is required");
        }
        if (requestDTO.getEntries() == null || requestDTO.getEntries().isEmpty()) {
            throw new InvalidRequestException("At least one bulk payment entry is required");
        }

        MerchVariantItem merchVariantItem = merchVariantItemRepository.findByIdWithLock(requestDTO.getMerchVariantItemId())
                .orElseThrow(() -> new MerchNotFoundException(
                        "MerchVariantItem not found with ID: " + requestDTO.getMerchVariantItemId()));

        int quantityPerStudent = requestDTO.getQuantity() != null ? requestDTO.getQuantity() : 1;
        if (quantityPerStudent <= 0) {
            throw new InvalidRequestException("Quantity must be greater than 0");
        }

        MerchType merchType = merchVariantItem.getMerchVariant().getMerch().getMerchType();
        Long merchId = merchVariantItem.getMerchVariant().getMerch().getMerchId();
        if (merchType == MerchType.MEMBERSHIP) {
            throw new InvalidRequestException("Bulk merch payment does not support membership items. Use the membership payment flow.");
        }
        if (merchType == MerchType.TICKET && quantityPerStudent > 1) {
            throw new InvalidRequestException("Quantity for ticket items must remain 1");
        }

        double pricePerItem = merchVariantItem.getPrice();
        int totalStockNeeded = quantityPerStudent * requestDTO.getEntries().size();
        if (merchVariantItem.getStockQuantity() < totalStockNeeded) {
            throw new InvalidRequestException(
                    "Insufficient stock. Available: " + merchVariantItem.getStockQuantity()
                            + ", Required: " + totalStockNeeded);
        }

        try {
            List<Order> ordersToSave = new java.util.ArrayList<>();
            List<Student> students = new java.util.ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            Set<String> seenStudentIds = new LinkedHashSet<>();

            for (BulkPaymentEntryDTO entry : requestDTO.getEntries()) {
                String studentId = normalizeStudentId(entry);
                if (!seenStudentIds.add(studentId)) {
                    throw new InvalidRequestException("Duplicate student ID in bulk payment request: " + studentId);
                }
                if (merchType == MerchType.TICKET) {
                    validateTicketBulkPaymentEligibility(studentId, merchId);
                }

                Student student = studentRepository.findByStudentId(studentId)
                        .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));
                students.add(student);

                ordersToSave.add(Order.builder()
                        .student(student)
                        .orderDate(entry.getOrderDate())
                        .totalPrice(pricePerItem * quantityPerStudent)
                        .updatedAt(now)
                        .orderStatus(OrderStatus.TO_BE_CLAIMED)
                        .quantity(quantityPerStudent)
                        .build());
            }

            List<Order> savedOrders = orderRepository.saveAll(ordersToSave);
            List<OrderItem> orderItemsToSave = new java.util.ArrayList<>();

            for (int index = 0; index < savedOrders.size(); index++) {
                Order savedOrder = savedOrders.get(index);
                orderItemsToSave.add(OrderItem.builder()
                        .order(savedOrder)
                        .merchVariantItem(merchVariantItem)
                        .quantity(quantityPerStudent)
                        .priceAtPurchase(pricePerItem)
                        .orderStatus(OrderStatus.TO_BE_CLAIMED)
                        .build());
            }

            List<OrderItem> savedItems = orderItemRepository.saveAll(orderItemsToSave);
            for (OrderItem savedItem : savedItems) {
                if (savedItem.getMerchVariantItem().getMerchVariant().getMerch().getMerchType() == MerchType.TICKET) {
                    ticketFreebieAssignmentService.initializeAssignments(savedItem.getOrderItemId(), List.of());
                }
            }

            merchVariantItem.setStockQuantity(merchVariantItem.getStockQuantity() - totalStockNeeded);
            merchVariantItemRepository.save(merchVariantItem);

            return savedOrders.stream().map(orderMapper::toResponseDTO).toList();
        } catch (Exception e) {
            throw e instanceof InvalidRequestException ? (InvalidRequestException) e
                : new InvalidRequestException("Failed to record bulk merch payment: " + e.getMessage());
        }
    }

    @Override
    public boolean hasMembershipInCartOrPendingOrder(String studentId) {
        boolean inCart = cartItemRepository.existsByStudentIdAndMerchType(studentId, MerchType.MEMBERSHIP);
        boolean inPendingOrder = orderItemRepository.existsByStudentIdAndMerchTypeAndStatus(
                studentId, MerchType.MEMBERSHIP, OrderStatus.PENDING);
        return inCart || inPendingOrder;
    }

    @Override
    public List<MerchCustomerResponseDTO> getAllCustomersByMerchId(Long merchId, boolean includeFreebies) {
        validateMerchExists(merchId);
        List<OrderItem> orderItems = orderItemRepository.findAllByMerchId(merchId);
        Map<Long, List<TicketFreebieAssignmentResponseDTO>> assignmentsByOrderItemId =
                loadAssignments(orderItems, includeFreebies);
        return orderItems.stream()
                .map(orderItem -> mapToMerchCustomerDTO(
                        orderItem,
                        assignmentsByOrderItemId.get(orderItem.getOrderItemId()),
                        includeFreebies))
                .toList();
    }

    private MerchCustomerResponseDTO mapToMerchCustomerDTO(
            OrderItem orderItem,
            List<TicketFreebieAssignmentResponseDTO> freebieAssignments,
            boolean includeFreebies) {
        var order = orderItem.getOrder();
        var student = order.getStudent();
        var userProfile = student.getUserAccount().getUserProfile();
        var merchVariantItem = orderItem.getMerchVariantItem();
        var merchVariant = merchVariantItem.getMerchVariant();
        var merch = merchVariant.getMerch();

        String studentName = (userProfile.getFirstName() != null ? userProfile.getFirstName() : "")
                + " " + (userProfile.getLastName() != null ? userProfile.getLastName() : "");

        return MerchCustomerResponseDTO.builder()
                .orderItemId(orderItem.getOrderItemId())
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
                .hasFreebie(resolveHasFreebie(merchTypeOf(merch), merch, freebieAssignments, includeFreebies))
                .freebieAssignments(includeFreebies ? defaultAssignments(freebieAssignments) : null)
                .build();
    }

    private Map<Long, List<TicketFreebieAssignmentResponseDTO>> loadAssignments(
            List<OrderItem> orderItems,
            boolean includeFreebies) {
        if (!includeFreebies || orderItems == null || orderItems.isEmpty()) {
            return Map.of();
        }

        List<Long> orderItemIds = orderItems.stream()
                .map(OrderItem::getOrderItemId)
                .filter(id -> id != null)
                .toList();
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }

        return ticketFreebieAssignmentService.getAssignmentsByOrderItemIds(orderItemIds);
    }

    private Boolean resolveHasFreebie(
            MerchType merchType,
            org.csps.backend.domain.entities.Merch merch,
            List<TicketFreebieAssignmentResponseDTO> freebieAssignments,
            boolean includeFreebies) {
        if (!includeFreebies) {
            return merchType == MerchType.TICKET && Boolean.TRUE.equals(merch.getHasFreebie());
        }

        return defaultAssignments(freebieAssignments).stream()
                .anyMatch(assignment -> Boolean.TRUE.equals(assignment.getHasFreebie()));
    }

    private List<TicketFreebieAssignmentResponseDTO> defaultAssignments(
            List<TicketFreebieAssignmentResponseDTO> freebieAssignments) {
        return freebieAssignments == null ? List.of() : freebieAssignments;
    }

    private MerchType merchTypeOf(org.csps.backend.domain.entities.Merch merch) {
        return merch == null ? null : merch.getMerchType();
    }

    private String normalizeStudentId(BulkPaymentEntryDTO entry) {
        if (entry == null) {
            throw new InvalidRequestException("Bulk payment entry is required");
        }
        if (entry.getStudentId() == null || entry.getStudentId().trim().isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        if (entry.getOrderDate() == null) {
            throw new InvalidRequestException("Order date is required");
        }
        return entry.getStudentId().trim();
    }

    private void validateTicketBulkPaymentEligibility(String studentId, Long merchId) {
        if (cartItemRepository.existsByStudentIdAndMerchId(studentId, merchId)
                || orderItemRepository.existsByStudentIdAndMerchIdAndOrderStatusNotIn(
                        studentId,
                        merchId,
                        List.of(OrderStatus.CANCELLED, OrderStatus.REJECTED))) {
            throw new InvalidRequestException("Item is already in the cart / order");
        }
    }

    private void validateMerchExists(Long merchId) {
        if (!merchRepository.existsById(merchId)) {
            throw new MerchNotFoundException("Merch not found with ID: " + merchId);
        }
    }
}
