package org.csps.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.csps.backend.domain.dtos.request.OrderItemRequestDTO;
import org.csps.backend.domain.dtos.request.TicketFreebieAssignmentRequestDTO;
import org.csps.backend.domain.dtos.response.OrderItemResponseDTO;
import org.csps.backend.domain.entities.MerchVariantItem;
import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.entities.OrderItem;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.exception.InvalidOrderStatusTransitionException;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.OrderItemNotFoundException;
import org.csps.backend.exception.OrderNotFoundException;
import org.csps.backend.mapper.OrderItemMapper;
import org.csps.backend.repository.MerchVariantItemRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.OrderRepository;
import org.csps.backend.repository.TicketFreebieAssignmentRepository;
import org.csps.backend.service.OrderItemService;
import org.csps.backend.service.OrderLifecycleService;
import org.csps.backend.service.OrderNotificationService;
import org.csps.backend.service.StudentMembershipService;
import org.csps.backend.service.TicketFreebieAssignmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderItemServiceImpl implements OrderItemService {

    private static final String ITEM_ALREADY_IN_CART_OR_ORDER = "Item is already in the cart / order";

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final MerchVariantItemRepository merchVariantItemRepository;
    private final OrderItemMapper orderItemMapper;
    private final OrderNotificationService orderNotificationService;
    private final OrderLifecycleService orderLifecycleService;
    private final TicketFreebieAssignmentService ticketFreebieAssignmentService;
    private final TicketFreebieAssignmentRepository ticketFreebieAssignmentRepository;
    private final StudentMembershipService studentMembershipService;

    @Override
    @Transactional
    public OrderItemResponseDTO createOrderItem(OrderItemRequestDTO orderItemRequestDTO) {
        if (orderItemRequestDTO == null) {
            throw new InvalidRequestException("Order item request is required");
        }

        Order order = orderRepository.findById(orderItemRequestDTO.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException("Order not found"));

        MerchVariantItem merchVariantItem = merchVariantItemRepository.findByIdWithLock(orderItemRequestDTO.getMerchVariantItemId())
                .orElseThrow(() -> new InvalidRequestException("MerchVariantItem not found"));

        if (orderItemRequestDTO.getQuantity() == null || orderItemRequestDTO.getQuantity() <= 0) {
            throw new InvalidRequestException("Quantity must be greater than 0");
        }
        if (orderItemRequestDTO.getQuantity() > merchVariantItem.getStockQuantity()) {
            throw new InvalidRequestException("Insufficient stock. Available: " + merchVariantItem.getStockQuantity()
                    + ", Requested: " + orderItemRequestDTO.getQuantity());
        }

        Double priceSnapshot = merchVariantItem.getPrice();
        if (priceSnapshot == null || priceSnapshot < 0) {
            throw new InvalidRequestException("MerchVariantItem price must be configured before creating an order item");
        }
        validateTicketPurchaseEligibility(order, merchVariantItem);

        try {
            OrderItem orderItem = OrderItem.builder()
                .order(order)
                .merchVariantItem(merchVariantItem)
                .quantity(orderItemRequestDTO.getQuantity())
                .priceAtPurchase(priceSnapshot)
                .updatedAt(LocalDateTime.now())
                .build();

            OrderItem savedOrderItem = orderItemRepository.save(orderItem);

            merchVariantItem.setStockQuantity(merchVariantItem.getStockQuantity() - orderItemRequestDTO.getQuantity());
            merchVariantItemRepository.save(merchVariantItem);

            if (merchVariantItem.getMerchVariant() != null
                && merchVariantItem.getMerchVariant().getMerch() != null
                && merchVariantItem.getMerchVariant().getMerch().getMerchType() == MerchType.TICKET) {
                ticketFreebieAssignmentService.initializeAssignments(
                    savedOrderItem.getOrderItemId(),
                    resolveAssignmentRequests(orderItemRequestDTO)
                );
            }

            return enrichResponse(orderItemMapper.toResponseDTO(savedOrderItem));
        } catch (Exception e) {
            throw e instanceof InvalidRequestException ? (InvalidRequestException) e
                : new InvalidRequestException("Failed to create order item: " + e.getMessage());
        }
    }

    @Override
    public OrderItemResponseDTO getOrderItemById(Long id, String studentId) {
        if (id == null || id <= 0) {
            throw new InvalidRequestException("Invalid order item ID");
        }

        String studentScope = normalizeStudentScope(studentId);
        OrderItem orderItem = studentScope == null
            ? orderItemRepository.findByIdWithStudentAndMerchDetails(id)
                .orElseThrow(() -> new OrderItemNotFoundException("Order item not found"))
            : orderItemRepository.findByOrderItemIdAndOrderStudentStudentId(id, studentScope)
                .orElseThrow(() -> new OrderItemNotFoundException("Order item not found"));

        return enrichResponse(orderItemMapper.toResponseDTO(orderItem));
    }

    @Override
    public Page<OrderItemResponseDTO> getOrderItemsByStatus(OrderStatus status, Pageable pageable, String studentId) {
        if (status == null) {
            throw new InvalidRequestException("Order status is required");
        }

        String studentScope = normalizeStudentScope(studentId);
        Page<OrderItem> orderItems = studentScope == null
            ? orderItemRepository.findByOrderStatusOrderByUpdatedAtDesc(status, pageable)
            : orderItemRepository.findByOrderStatusAndOrderStudentStudentId(status, studentScope, pageable);

        return orderItems.map(orderItemMapper::toResponseDTO).map(this::enrichResponse);
    }

    @Override
    public List<OrderItemResponseDTO> getOrderItemsByOrderId(Long orderId, String studentId) {
        if (orderId == null || orderId <= 0) {
            throw new InvalidRequestException("Invalid order ID");
        }

        String studentScope = normalizeStudentScope(studentId);
        validateOrderAccess(orderId, studentScope);

        List<OrderItem> orderItems = studentScope == null
            ? orderItemRepository.findByOrderOrderId(orderId)
            : orderItemRepository.findByOrderOrderIdAndOrderStudentStudentId(orderId, studentScope);

        return orderItems.stream()
            .map(orderItemMapper::toResponseDTO)
            .map(this::enrichResponse)
            .toList();
    }

    @Override
    public Page<OrderItemResponseDTO> getOrderItemsByOrderIdPaginated(Long orderId, Pageable pageable, String studentId) {
        if (orderId == null || orderId <= 0) {
            throw new InvalidRequestException("Invalid order ID");
        }

        String studentScope = normalizeStudentScope(studentId);
        validateOrderAccess(orderId, studentScope);

        Page<OrderItem> orderItems = studentScope == null
            ? orderItemRepository.findByOrderOrderId(orderId, pageable)
            : orderItemRepository.findByOrderOrderIdAndOrderStudentStudentId(orderId, studentScope, pageable);

        return orderItems.map(orderItemMapper::toResponseDTO).map(this::enrichResponse);
    }

    @Override
    public Page<OrderItemResponseDTO> getOrderItemsByStudentIdPaginated(String studentId, Pageable pageable) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }

        Page<OrderItem> orderItems = orderItemRepository.findByOrderStudentStudentIdOrderByUpdatedAtDesc(studentId, pageable);
        return orderItems.map(orderItemMapper::toResponseDTO).map(this::enrichResponse);
    }

    @Override
    public Page<OrderItemResponseDTO> getOrderItemsByStudentIdAndStatusPaginated(String studentId, OrderStatus status, Pageable pageable) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        if (status == null) {
            throw new InvalidRequestException("Order status is required");
        }

        Page<OrderItem> orderItems = orderItemRepository.findByOrderStudentStudentIdAndOrderStatusOrderByUpdatedAtDesc(studentId, status, pageable);
        return orderItems.map(orderItemMapper::toResponseDTO).map(this::enrichResponse);
    }

    @Override
    @Transactional
    public OrderItemResponseDTO updateOrderItemStatus(Long id, OrderStatus status) {
        if (id == null || id <= 0) {
            throw new InvalidRequestException("Invalid order item ID");
        }
        if (status == null) {
            throw new InvalidRequestException("Order status is required");
        }
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStatusTransitionException("Use the order cancellation flow to set CANCELLED status");
        }

        OrderItem orderItem = orderItemRepository.findById(id)
            .orElseThrow(() -> new OrderItemNotFoundException("Order item not found"));

        OrderStatus oldStatus = orderItem.getOrderStatus();
        Order order = orderItem.getOrder();

        if (order.getOrderStatus() == OrderStatus.REJECTED || order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderStatusTransitionException("Terminal orders cannot be updated through item status changes");
        }

        if (status == OrderStatus.REJECTED) {
            List<OrderItem> siblingItems = order.getOrderItems();
            if (siblingItems != null && siblingItems.size() > 1) {
                throw new InvalidOrderStatusTransitionException("Use the order rejection flow for multi-item orders");
            }

            orderLifecycleService.applyTerminalStatus(order, OrderStatus.REJECTED);
            OrderItem updatedItem = orderItemRepository.findByIdWithStudentAndMerchDetails(id)
                    .orElseThrow(() -> new OrderItemNotFoundException("Order item not found"));
            return enrichResponse(orderItemMapper.toResponseDTO(updatedItem));
        }

        validateNonTerminalStatusTransition(oldStatus, status);
        if (oldStatus == status) {
            return enrichResponse(orderItemMapper.toResponseDTO(orderItem));
        }

        try {
            orderItem.setOrderStatus(status);
            orderItem.setUpdatedAt(LocalDateTime.now());

            OrderStatus effectiveStatus = resolveEffectiveApprovedStatus(orderItem, status);
            orderItem.setOrderStatus(effectiveStatus);

            OrderItem updatedOrderItem = orderItemRepository.save(orderItem);
            syncParentOrderStatus(order);

            activateMembershipWhenAccepted(updatedOrderItem, effectiveStatus);

            OrderItem itemWithDetails = orderItemRepository.findByIdWithStudentAndMerchDetails(id).orElse(null);
            if (itemWithDetails != null) {
                var notificationData = orderNotificationService.extractNotificationData(itemWithDetails, effectiveStatus);
                if (notificationData != null) {
                    orderNotificationService.sendOrderStatusEmail(notificationData);
                }
            }

            return enrichResponse(orderItemMapper.toResponseDTO(updatedOrderItem));
        } catch (InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidRequestException("Failed to update order item status: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteOrderItem(Long id) {
        if (id == null || id <= 0) {
            throw new InvalidRequestException("Invalid order item ID");
        }
        if (!orderItemRepository.existsById(id)) {
            throw new OrderItemNotFoundException("Order item not found");
        }
        ticketFreebieAssignmentRepository.deleteByOrderItemOrderItemId(id);
        orderItemRepository.deleteById(id);
    }

    private OrderItemResponseDTO enrichResponse(OrderItemResponseDTO response) {
        response.setFreebieAssignments(ticketFreebieAssignmentService.getAssignmentsByOrderItemId(response.getOrderItemId()));
        return response;
    }

    private List<TicketFreebieAssignmentRequestDTO> resolveAssignmentRequests(OrderItemRequestDTO request) {
        if (request.getFreebieSelections() != null && !request.getFreebieSelections().isEmpty()) {
            return request.getFreebieSelections().stream()
                    .map(selection -> TicketFreebieAssignmentRequestDTO.builder()
                            .ticketFreebieConfigId(selection.getTicketFreebieConfigId())
                            .selectedSize(selection.getSelectedSize())
                            .selectedColor(selection.getSelectedColor())
                            .selectedDesign(selection.getSelectedDesign())
                            .build())
                    .toList();
        }

        if (request.getFreebieAssignments() != null && !request.getFreebieAssignments().isEmpty()) {
            return request.getFreebieAssignments().stream()
                    .map(assignment -> TicketFreebieAssignmentRequestDTO.builder()
                            .ticketFreebieConfigId(assignment.getTicketFreebieConfigId())
                            .selectedSize(assignment.getSelectedSize())
                            .selectedColor(assignment.getSelectedColor())
                            .selectedDesign(assignment.getSelectedDesign())
                            .fulfillmentStatus(assignment.getFulfillmentStatus())
                            .build())
                    .toList();
        }

        return List.of();
    }

    private void validateTicketPurchaseEligibility(Order order, MerchVariantItem merchVariantItem) {
        if (order == null || order.getStudent() == null || merchVariantItem == null
                || merchVariantItem.getMerchVariant() == null || merchVariantItem.getMerchVariant().getMerch() == null) {
            return;
        }

        if (merchVariantItem.getMerchVariant().getMerch().getMerchType() != MerchType.TICKET) {
            return;
        }

        String studentId = order.getStudent().getStudentId();
        Long merchId = merchVariantItem.getMerchVariant().getMerch().getMerchId();

        // Student-facing ticket purchases should stop once the same ticket merch already exists
        // in another active order. Cart ownership is enforced during add-to-cart and surfaced in
        // merch responses so the frontend can disable purchase actions before checkout.
        boolean alreadyInActiveOrder = orderItemRepository.existsByStudentIdAndMerchIdAndOrderStatusNotIn(
                studentId,
                merchId,
                List.of(OrderStatus.CANCELLED, OrderStatus.REJECTED));
        if (alreadyInActiveOrder) {
            throw new InvalidRequestException(ITEM_ALREADY_IN_CART_OR_ORDER);
        }
    }

    private void activateMembershipWhenAccepted(OrderItem orderItem, OrderStatus newStatus) {
        if (orderItem == null || newStatus == null) {
            return;
        }
        if (newStatus != OrderStatus.TO_BE_CLAIMED && newStatus != OrderStatus.CLAIMED) {
            return;
        }
        if (orderItem.getMerchVariantItem() == null
                || orderItem.getMerchVariantItem().getMerchVariant() == null
                || orderItem.getMerchVariantItem().getMerchVariant().getMerch() == null
                || orderItem.getOrder() == null
                || orderItem.getOrder().getStudent() == null) {
            return;
        }

        if (orderItem.getMerchVariantItem().getMerchVariant().getMerch().getMerchType() != MerchType.MEMBERSHIP) {
            return;
        }

        String studentId = orderItem.getOrder().getStudent().getStudentId();
        if (studentId == null || studentId.isBlank()) {
            return;
        }

        // Keep membership records aligned with accepted membership orders across all approval paths.
        studentMembershipService.ensureMembershipForCurrentAcademicYear(studentId);
    }

    private OrderStatus resolveEffectiveApprovedStatus(OrderItem orderItem, OrderStatus requestedStatus) {
        if (requestedStatus == OrderStatus.TO_BE_CLAIMED && isMembershipItem(orderItem)) {
            return OrderStatus.CLAIMED;
        }

        return requestedStatus;
    }

    private boolean isMembershipItem(OrderItem orderItem) {
        return orderItem != null
                && orderItem.getMerchVariantItem() != null
                && orderItem.getMerchVariantItem().getMerchVariant() != null
                && orderItem.getMerchVariantItem().getMerchVariant().getMerch() != null
                && orderItem.getMerchVariantItem().getMerchVariant().getMerch().getMerchType() == MerchType.MEMBERSHIP;
    }

    private String normalizeStudentScope(String studentId) {
        if (studentId == null) {
            return null;
        }
        String trimmedStudentId = studentId.trim();
        return trimmedStudentId.isEmpty() ? null : trimmedStudentId;
    }

    private void validateOrderAccess(Long orderId, String studentId) {
        boolean orderAccessible = studentId == null
            ? orderRepository.existsById(orderId)
            : orderRepository.findByOrderIdAndStudentStudentId(orderId, studentId).isPresent();

        if (!orderAccessible) {
            throw new OrderNotFoundException("Order not found");
        }
    }

    private void validateNonTerminalStatusTransition(OrderStatus oldStatus, OrderStatus newStatus) {
        if (newStatus != OrderStatus.TO_BE_CLAIMED && newStatus != OrderStatus.CLAIMED) {
            throw new InvalidOrderStatusTransitionException("Only TO_BE_CLAIMED or CLAIMED are allowed through item updates");
        }
        if (oldStatus == OrderStatus.REJECTED || oldStatus == OrderStatus.CANCELLED) {
            throw new InvalidOrderStatusTransitionException("Terminal order items cannot be updated");
        }
        if (oldStatus == OrderStatus.PENDING && newStatus == OrderStatus.CLAIMED) {
            throw new InvalidOrderStatusTransitionException("Pending items must move to TO_BE_CLAIMED before CLAIMED");
        }
    }

    private void syncParentOrderStatus(Order order) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }
        if (order.getOrderStatus() == OrderStatus.REJECTED || order.getOrderStatus() == OrderStatus.CANCELLED) {
            return;
        }

        boolean allClaimed = order.getOrderItems().stream().allMatch(item -> item.getOrderStatus() == OrderStatus.CLAIMED);
        boolean anyReadyOrClaimed = order.getOrderItems().stream()
                .anyMatch(item -> item.getOrderStatus() == OrderStatus.TO_BE_CLAIMED || item.getOrderStatus() == OrderStatus.CLAIMED);

        OrderStatus derivedStatus = allClaimed
                ? OrderStatus.CLAIMED
                : anyReadyOrClaimed ? OrderStatus.TO_BE_CLAIMED : OrderStatus.PENDING;

        if (order.getOrderStatus() != derivedStatus) {
            order.setOrderStatus(derivedStatus);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
    }
}
