package org.csps.backend.service;

import java.util.List;

import org.csps.backend.domain.dtos.request.OrderPostRequestDTO;
import org.csps.backend.domain.dtos.request.OrderSearchDTO;
import org.csps.backend.domain.dtos.response.OrderResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    /**
     * Create one or more orders for a student.
     * This creates Order(s) and delegates OrderItem creation to OrderItemService.
     */
     OrderResponseDTO createOrder(String studentId, OrderPostRequestDTO orderRequests);
    
    /**
     * Get all orders (admin only).
     */
    List<OrderResponseDTO> getAllOrders();
    
    /**
     * Get paginated orders (admin only).
     */
    Page<OrderResponseDTO> getAllOrdersPaginated(Pageable pageable);
    
    /**
     * Get order by ID.
     */
    OrderResponseDTO getOrderById(Long orderId, String studentId);
    
    /**
     * Get all orders for a specific student.
     */
    List<OrderResponseDTO> getOrdersByStudentId(String studentId);
    
    /**
     * Get paginated orders for a specific student.
     */
    Page<OrderResponseDTO> getOrdersByStudentIdPaginated(String studentId, Pageable pageable);
    
    Page<OrderResponseDTO> getAllOrdersPaginatedSortByDate(Pageable pageable);
    
    /**
     * Search orders by filters (student name, id, status, date range).
     * All filter fields are optional.
     * Dynamic pagination and sorting.
     */
    Page<OrderResponseDTO> searchOrders(OrderSearchDTO searchDTO, Pageable pageable, String studentId);

    /**
     * Delete order and all its items.
     */
    void deleteOrder(Long orderId);

    /**
     * Cancel a pending order owned by the authenticated student.
     */
    OrderResponseDTO cancelOrder(String studentId, Long orderId);
}

