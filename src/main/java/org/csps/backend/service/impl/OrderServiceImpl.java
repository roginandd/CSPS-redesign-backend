package org.csps.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.csps.backend.domain.dtos.request.OrderItemRequestDTO;
import org.csps.backend.domain.dtos.request.OrderPostRequestDTO;
import org.csps.backend.domain.dtos.request.OrderSearchDTO;
import org.csps.backend.domain.dtos.request.TicketFreebieSelectionRequestDTO;
import org.csps.backend.domain.dtos.response.CartItemFreebieSelectionItemResponseDTO;
import org.csps.backend.domain.dtos.response.CartItemResponseDTO;
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
import org.csps.backend.repository.TicketFreebieAssignmentRepository;
import org.csps.backend.repository.specification.OrderSpecification;
import org.csps.backend.service.CartItemService;
import org.csps.backend.service.OrderItemService;
import org.csps.backend.service.OrderLifecycleService;
import org.csps.backend.service.OrderService;
import org.csps.backend.service.TicketFreebieAssignmentService;
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
    private final TicketFreebieAssignmentService ticketFreebieAssignmentService;
    private final TicketFreebieAssignmentRepository ticketFreebieAssignmentRepository;

    @Override
    @Transactional
    public OrderResponseDTO createOrder(String studentId, OrderPostRequestDTO orderRequests) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        if (orderRequests == null || orderRequests.getOrderItems() == null || orderRequests.getOrderItems().isEmpty()) {
            throw new InvalidRequestException("At least one order request is required");
        }

        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found"));

        Order order = Order.builder()
                .student(student)
                .orderDate(LocalDateTime.now())
                .totalPrice(0.0)
                .updatedAt(LocalDateTime.now())
                .orderStatus(OrderStatus.PENDING)
                .quantity(0)
                .build();

        Order savedOrder = orderRepository.save(order);
        List<OrderItemRequestDTO> orderItemRequests = prepareOrderItemsFromCart(
                studentId,
                savedOrder.getOrderId(),
                orderRequests.getOrderItems());

        double totalPrice = 0.0;
        int totalQuantity = 0;
        for (OrderItemRequestDTO itemRequest : orderItemRequests) {
            OrderItemResponseDTO orderItemResponse = orderItemService.createOrderItem(itemRequest);
            totalPrice += orderItemResponse.getTotalPrice();
            totalQuantity += itemRequest.getQuantity() == null ? 0 : itemRequest.getQuantity();

            try {
                cartItemService.removeCartItem(studentId, itemRequest.getMerchVariantItemId());
            } catch (CartItemNotFoundException ignored) {
            }
        }

        savedOrder.setTotalPrice(totalPrice);
        savedOrder.setQuantity(totalQuantity);
        orderRepository.save(savedOrder);

        Order reloadedOrder = orderRepository.findByOrderIdAndStudentStudentId(savedOrder.getOrderId(), studentId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        return enrichOrderResponse(orderMapper.toResponseDTO(reloadedOrder));
    }

    @Override
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toResponseDTO)
                .map(this::enrichOrderResponse)
                .toList();
    }

    @Override
    public Page<OrderResponseDTO> getAllOrdersPaginated(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(orderMapper::toResponseDTO)
                .map(this::enrichOrderResponse);
    }

    @Override
    public OrderResponseDTO getOrderById(Long orderId, String studentId) {
        if (orderId == null || orderId <= 0) {
            throw new InvalidRequestException("Invalid order ID");
        }

        Order order = studentId == null || studentId.isBlank()
                ? orderRepository.findByOrderId(orderId).orElseThrow(() -> new OrderNotFoundException("Order not found"))
                : orderRepository.findByOrderIdAndStudentStudentId(orderId, studentId)
                        .orElseThrow(() -> new OrderNotFoundException("Order not found"));

        return enrichOrderResponse(orderMapper.toResponseDTO(order));
    }

    @Override
    public Page<OrderResponseDTO> getAllOrdersPaginatedSortByDate(Pageable pageable) {
        return orderRepository.findAllByOrderByOrderDateDesc(pageable)
                .map(orderMapper::toResponseDTO)
                .map(this::enrichOrderResponse);
    }

    @Override
    public List<OrderResponseDTO> getOrdersByStudentId(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException("Student not found");
        }

        return orderRepository.findByStudentId(studentId).stream()
                .map(orderMapper::toResponseDTO)
                .map(this::enrichOrderResponse)
                .toList();
    }

    @Override
    public Page<OrderResponseDTO> getOrdersByStudentIdPaginated(String studentId, Pageable pageable) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException("Student not found");
        }

        return orderRepository.findByStudentId(studentId, pageable)
                .map(orderMapper::toResponseDTO)
                .map(this::enrichOrderResponse);
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
            List<Long> orderItemIds = order.getOrderItems().stream()
                    .map(item -> item.getOrderItemId())
                    .filter(id -> id != null)
                    .toList();
            if (!orderItemIds.isEmpty()) {
                ticketFreebieAssignmentRepository.deleteByOrderItemOrderItemIdIn(orderItemIds);
            }
            orderItemRepository.deleteAllInBatch(order.getOrderItems());
        }

        orderRepository.delete(order);
    }

    @Override
    public Page<OrderResponseDTO> searchOrders(OrderSearchDTO searchDTO, Pageable pageable, String studentId) {
        OrderSearchDTO normalizedSearch = normalizeAndValidateSearch(searchDTO);
        Specification<Order> spec = OrderSpecification.withFilters(normalizedSearch, studentId);
        return orderRepository.findAll(spec, pageable)
                .map(orderMapper::toResponseDTO)
                .map(this::enrichOrderResponse);
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
        return enrichOrderResponse(orderMapper.toResponseDTO(cancelledOrder));
    }

    private List<OrderItemRequestDTO> prepareOrderItemsFromCart(
            String studentId,
            Long orderId,
            List<OrderItemRequestDTO> rawRequests) {
        for (OrderItemRequestDTO rawRequest : rawRequests) {
            rawRequest.setOrderId(orderId);

            if (rawRequest.getFreebieSelections() != null && !rawRequest.getFreebieSelections().isEmpty()) {
                continue;
            }

            try {
                CartItemResponseDTO cartItem = cartItemService.getCartItemByMerchVariantItemId(studentId, rawRequest.getMerchVariantItemId());
                rawRequest.setQuantity(cartItem.getQuantity());
                rawRequest.setFreebieSelections(cartItem.getFreebieSelections() == null ? List.of() : cartItem.getFreebieSelections().stream()
                        .map(this::toSelectionRequest)
                        .toList());
            } catch (CartItemNotFoundException ignored) {
            }
        }

        return rawRequests;
    }

    private OrderResponseDTO enrichOrderResponse(OrderResponseDTO response) {
        if (response == null || response.getOrderItems() == null || response.getOrderItems().isEmpty()) {
            return response;
        }

        List<Long> orderItemIds = response.getOrderItems().stream()
                .map(OrderItemResponseDTO::getOrderItemId)
                .filter(id -> id != null)
                .toList();
        Map<Long, List<org.csps.backend.domain.dtos.response.TicketFreebieAssignmentResponseDTO>> assignments =
                ticketFreebieAssignmentService.getAssignmentsByOrderItemIds(orderItemIds);

        response.getOrderItems().forEach(orderItem ->
                orderItem.setFreebieAssignments(assignments.get(orderItem.getOrderItemId())));
        return response;
    }

    private TicketFreebieSelectionRequestDTO toSelectionRequest(CartItemFreebieSelectionItemResponseDTO selection) {
        return TicketFreebieSelectionRequestDTO.builder()
                .ticketFreebieConfigId(selection.getTicketFreebieConfigId())
                .selectedSize(selection.getSelectedSize())
                .selectedColor(selection.getSelectedColor())
                .selectedDesign(selection.getSelectedDesign())
                .build();
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
