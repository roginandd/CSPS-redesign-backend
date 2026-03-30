package org.csps.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.csps.backend.domain.dtos.request.OrderItemRequestDTO;
import org.csps.backend.domain.dtos.response.OrderItemResponseDTO;
import org.csps.backend.domain.entities.MerchVariantItem;
import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.entities.OrderItem;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.exception.InvalidOrderStatusTransitionException;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.OrderItemNotFoundException;
import org.csps.backend.exception.OrderNotFoundException;
import org.csps.backend.mapper.OrderItemMapper;
import org.csps.backend.repository.MerchVariantItemRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.OrderRepository;
import org.csps.backend.service.OrderItemService;
import org.csps.backend.service.OrderLifecycleService;
import org.csps.backend.service.OrderNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderItemServiceImpl implements OrderItemService {
    
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final MerchVariantItemRepository merchVariantItemRepository;
    private final OrderItemMapper orderItemMapper;
    private final OrderNotificationService orderNotificationService;
    private final OrderLifecycleService orderLifecycleService;
    
    @Override
    @Transactional
    public OrderItemResponseDTO createOrderItem(OrderItemRequestDTO orderItemRequestDTO) {
        if (orderItemRequestDTO == null) {
            throw new InvalidRequestException("Order item request is required");
        }
        
        /* validate order exists */
        Order order = orderRepository.findById(orderItemRequestDTO.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        
        /* validate merch variant item exists and acquire pessimistic write lock to prevent concurrent stock updates */
        MerchVariantItem merchVariantItem = merchVariantItemRepository.findByIdWithLock(orderItemRequestDTO.getMerchVariantItemId())
            .orElseThrow(() -> new InvalidRequestException("MerchVariantItem not found"));
        
        /* extract merchVariantId from MerchVariantItem */
        
        /* validate quantity */
        if (orderItemRequestDTO.getQuantity() == null || orderItemRequestDTO.getQuantity() <= 0) {
            throw new InvalidRequestException("Quantity must be greater than 0");
        }
        
        /* validate sufficient stock */
        if (orderItemRequestDTO.getQuantity() > merchVariantItem.getStockQuantity()) {
            throw new InvalidRequestException("Insufficient stock. Available: " + merchVariantItem.getStockQuantity() + 
                    ", Requested: " + orderItemRequestDTO.getQuantity());
        }

        Double priceSnapshot = merchVariantItem.getPrice();
        if (priceSnapshot == null || priceSnapshot < 0) {
            throw new InvalidRequestException("MerchVariantItem price must be configured before creating an order item");
        }
        
        try {
            /* snapshot the current SKU price on the server to prevent client-side tampering */
            OrderItem orderItem = OrderItem.builder()
                .order(order)
                .merchVariantItem(merchVariantItem)
                .quantity(orderItemRequestDTO.getQuantity())
                .priceAtPurchase(priceSnapshot)
                .updatedAt(LocalDateTime.now())
                .build();
            
            OrderItem savedOrderItem = orderItemRepository.save(orderItem);
            
            /* deduct stock from MerchVariantItem (pessimistic lock already held) */
            /* stock is reserved when order item is created */
            int newStockQuantity = merchVariantItem.getStockQuantity() - orderItemRequestDTO.getQuantity();
            merchVariantItem.setStockQuantity(newStockQuantity);
            merchVariantItemRepository.save(merchVariantItem);
            
            System.out.println("Order item created successfully. Stock deducted: " + orderItemRequestDTO.getQuantity());
            return orderItemMapper.toResponseDTO(savedOrderItem);
        } catch (Exception e) {
            /* log error and rethrow to trigger transaction rollback */
            System.err.println("Error creating order item and deducting stock: " + e.getMessage());
            e.printStackTrace();
            throw new InvalidRequestException("Failed to create order item: " + e.getMessage());
        }
    }
    
    /**
     * Helper method to extract MerchVariantId from MerchVariantItem
     */
    private Long getMerchVariantIdFromItem(MerchVariantItem merchVariantItem) {
        if (merchVariantItem == null) {
            throw new InvalidRequestException("MerchVariantItem is null");
        }
        if (merchVariantItem.getMerchVariant() == null) {
            throw new InvalidRequestException("MerchVariant is null for MerchVariantItem");
        }
        return merchVariantItem.getMerchVariant().getMerchVariantId();
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
        
        return orderItemMapper.toResponseDTO(orderItem);
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

        return orderItems.map(orderItemMapper::toResponseDTO);

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

        return orderItems.map(orderItemMapper::toResponseDTO);
    }
    
    @Override
    public Page<OrderItemResponseDTO> getOrderItemsByStudentIdPaginated(String studentId, Pageable pageable) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        Page<OrderItem> orderItems = orderItemRepository.findByOrderStudentStudentIdOrderByUpdatedAtDesc(studentId, pageable);
        return orderItems.map(orderItemMapper::toResponseDTO);
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
        return orderItems.map(orderItemMapper::toResponseDTO);
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
            return orderItemMapper.toResponseDTO(updatedItem);
        }

        validateNonTerminalStatusTransition(oldStatus, status);

        if (oldStatus == status) {
            return orderItemMapper.toResponseDTO(orderItem);
        }
        
        try {
            /* update order item status */
            orderItem.setOrderStatus(status);
            orderItem.setUpdatedAt(LocalDateTime.now());
            
            OrderItem updatedOrderItem = orderItemRepository.save(orderItem);
            syncParentOrderStatus(order);

            /* send notification email if order details are available */
            OrderItem itemWithDetails = orderItemRepository.findByIdWithStudentAndMerchDetails(id)
                .orElse(null);
            if (itemWithDetails != null) {
                var notificationData = orderNotificationService.extractNotificationData(itemWithDetails, status);
                if (notificationData != null) {
                    orderNotificationService.sendOrderStatusEmail(notificationData);
                }
            }

            return orderItemMapper.toResponseDTO(updatedOrderItem);
        } catch (InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            /* log error and rethrow to trigger transaction rollback */
            System.err.println("Error updating order item status: " + e.getMessage());
            e.printStackTrace();
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
        
        orderItemRepository.deleteById(id);
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

        boolean allClaimed = order.getOrderItems().stream()
                .allMatch(item -> item.getOrderStatus() == OrderStatus.CLAIMED);

        boolean anyReadyOrClaimed = order.getOrderItems().stream()
                .anyMatch(item -> item.getOrderStatus() == OrderStatus.TO_BE_CLAIMED
                        || item.getOrderStatus() == OrderStatus.CLAIMED);

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
