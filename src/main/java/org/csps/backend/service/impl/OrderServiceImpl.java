package org.csps.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.csps.backend.domain.dtos.request.OrderItemRequestDTO;
import org.csps.backend.domain.dtos.request.OrderPostRequestDTO;
import org.csps.backend.domain.dtos.request.OrderSearchDTO;
import org.csps.backend.domain.dtos.response.OrderItemResponseDTO;
import org.csps.backend.domain.dtos.response.OrderResponseDTO;
import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.exception.CartItemNotFoundException;
import org.csps.backend.exception.InvalidOrderStatusTransitionException;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.OrderNotFoundException;
import org.csps.backend.exception.StudentNotFoundException;
import org.csps.backend.mapper.OrderMapper;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.OrderRepository;
import org.csps.backend.repository.StudentRepository;
import org.csps.backend.repository.specification.OrderSpecification;
import org.csps.backend.service.CartItemService;
import org.csps.backend.service.MerchService;
import org.csps.backend.service.OrderLifecycleService;
import org.csps.backend.service.OrderItemService;
import org.csps.backend.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final StudentRepository studentRepository;


    private final OrderItemService orderItemService;
    private final OrderLifecycleService orderLifecycleService;
    private final CartItemService cartItemService;

    private final MerchService merchService;


    @Override
    @Transactional
    public OrderResponseDTO createOrder(String studentId, OrderPostRequestDTO orderRequests) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        if (orderRequests == null || orderRequests.getOrderItems() == null || orderRequests.getOrderItems().isEmpty()) {
            throw new InvalidRequestException("At least one order request is required");
        }
        
        // Validate student exists
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found"));
        
        // Create order
        Order order = Order.builder()
                .student(student)
                .orderDate(LocalDateTime.now())
                .totalPrice(0.0) // Will be updated after adding order items
                .updatedAt(LocalDateTime.now())
                .orderStatus(OrderStatus.PENDING) // Default status for new orders
                .quantity(0)
                .build();

        // Save order first to get the generated orderId
        Order savedOrder = orderRepository.save(order);

        List<OrderItemRequestDTO> orderItemRequests = orderRequests.getOrderItems();
        
        // Now set the orderId on all item requests
        orderItemRequests.forEach(req -> {
            req.setOrderId(savedOrder.getOrderId());
        });

        double totalPrice = 0.0;
        for (OrderItemRequestDTO itemRequest : orderItemRequests) {
            OrderItemResponseDTO orderItemResponse = orderItemService.createOrderItem(itemRequest);
            totalPrice += orderItemResponse.getTotalPrice();
            
            // Remove the item from cart after successful order item creation
            try {
                cartItemService.removeCartItem(studentId, itemRequest.getMerchVariantItemId());
            } catch (CartItemNotFoundException e) {
                // Item not in cart is OK - might have been removed already
            } catch (Exception e) {
                // Log other failures but don't fail the order
                System.err.println("Error: Failed to remove item from cart after ordering: " + e.getMessage());
                e.printStackTrace();
            }
        }

        savedOrder.setTotalPrice(totalPrice);
        orderRepository.save(savedOrder);
        
        return orderMapper.toResponseDTO(savedOrder);
    }

    @Override
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(orderMapper::toResponseDTO)
                .toList();
    }

    @Override
    public Page<OrderResponseDTO> getAllOrdersPaginated(Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);
        return orders.map(orderMapper::toResponseDTO);
    }

    @Override
    public OrderResponseDTO getOrderById(Long orderId, String studentId) {
        if (orderId == null || orderId <= 0) {
            throw new InvalidRequestException("Invalid order ID");
        }

        Order order = studentId == null || studentId.isBlank()
                ? orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found"))
                : orderRepository.findByOrderIdAndStudentStudentId(orderId, studentId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        
        return orderMapper.toResponseDTO(order);
    }

    @Override
    public Page<OrderResponseDTO> getAllOrdersPaginatedSortByDate(Pageable pageable) {
        Page<Order> orders = orderRepository.findAllByOrderByOrderDateDesc(pageable);
        return orders.map(orderMapper::toResponseDTO);
    }

    @Override
    public List<OrderResponseDTO> getOrdersByStudentId(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        // Verify student exists
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException("Student not found");
        }
        
        return orderRepository.findByStudentId(studentId)
                .stream()
                .map(orderMapper::toResponseDTO)
                .toList();
    }

    @Override
    public Page<OrderResponseDTO> getOrdersByStudentIdPaginated(String studentId, Pageable pageable) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        // Verify student exists
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException("Student not found");
        }
        
        Page<Order> orders = orderRepository.findByStudentId(studentId, pageable);
        return orders.map(orderMapper::toResponseDTO);
    }

    @Override
    @Transactional
    public void deleteOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new InvalidRequestException("Invalid order ID");
        }
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));

        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            orderItemRepository.deleteAllInBatch(order.getOrderItems());
        }

        orderRepository.delete(order);
    }

    @Override
    public Page<OrderResponseDTO> searchOrders(OrderSearchDTO searchDTO, Pageable pageable, String studentId) {
        OrderSearchDTO normalizedSearch = normalizeAndValidateSearch(searchDTO);
        Specification<Order> spec = OrderSpecification.withFilters(normalizedSearch, studentId);
        Page<Order> orders = orderRepository.findAll(spec, pageable);
        return orders.map(orderMapper::toResponseDTO);
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrder(String studentId, Long orderId) {
        if (studentId == null || studentId.isBlank()) {
            throw new InvalidRequestException("Student ID is required");
        }

        if (orderId == null || orderId <= 0) {
            throw new InvalidRequestException("Invalid order ID");
        }

        Order order = orderRepository.findByOrderIdAndStudentStudentId(orderId, studentId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderStatusTransitionException("Cancelled orders cannot be cancelled again");
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStatusTransitionException("Only pending orders can be cancelled");
        }

        if (order.getOrderItems() != null && order.getOrderItems().stream()
                .anyMatch(item -> item.getOrderStatus() != OrderStatus.PENDING)) {
            throw new InvalidOrderStatusTransitionException("Only orders with pending items can be cancelled");
        }

        Order cancelledOrder = orderLifecycleService.applyTerminalStatus(order, OrderStatus.CANCELLED);
        return orderMapper.toResponseDTO(cancelledOrder);
    }

    private OrderSearchDTO normalizeAndValidateSearch(OrderSearchDTO searchDTO) {
        OrderSearchDTO normalizedSearch = searchDTO == null ? new OrderSearchDTO() : searchDTO;

        normalizedSearch.setStudentName(normalizeWhitespace(normalizedSearch.getStudentName()));
        normalizedSearch.setStudentId(trimToNull(normalizedSearch.getStudentId()));
        normalizedSearch.setStatus(normalizeStatus(normalizedSearch.getStatus()));

        if (normalizedSearch.getStartDate() != null
                && normalizedSearch.getEndDate() != null
                && normalizedSearch.getEndDate().isBefore(normalizedSearch.getStartDate())) {
            throw new InvalidRequestException("End date must be greater than or equal to start date");
        }

        if (normalizedSearch.getStatus() != null) {
            try {
                OrderStatus.valueOf(normalizedSearch.getStatus());
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException("Invalid order status: " + normalizedSearch.getStatus());
            }
        }

        if (normalizedSearch.getYear() != null
                && (normalizedSearch.getYear() < 1000 || normalizedSearch.getYear() > 9999)) {
            throw new InvalidRequestException("Year filter must be a four-digit year");
        }

        return normalizedSearch;
    }

    private String normalizeStatus(String status) {
        String trimmedStatus = trimToNull(status);
        return trimmedStatus == null ? null : trimmedStatus.toUpperCase(Locale.ROOT);
    }

    private String normalizeWhitespace(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

