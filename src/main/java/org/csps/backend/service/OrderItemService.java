package org.csps.backend.service;

import java.util.List;

import org.csps.backend.domain.dtos.request.OrderItemRequestDTO;
import org.csps.backend.domain.dtos.response.OrderItemResponseDTO;
import org.csps.backend.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderItemService {
    
    /**
     * Create a new order item.
     */
    OrderItemResponseDTO createOrderItem(OrderItemRequestDTO orderItemRequestDTO);
    
    /**
     * Get order item by ID.
     */
    OrderItemResponseDTO getOrderItemById(Long id, String studentId);

    /**
     * Filter by order status
     *
     * @param pageable
     * @return
     */
    Page<OrderItemResponseDTO> getOrderItemsByStatus(OrderStatus status, Pageable pageable, String studentId);
    
    /**
     * Get all order items for a specific order.
     */
    List<OrderItemResponseDTO> getOrderItemsByOrderId(Long orderId, String studentId);
    

    /**
     * Get paginated order items for a specific order.
     */
    Page<OrderItemResponseDTO> getOrderItemsByOrderIdPaginated(Long orderId, Pageable pageable, String studentId);
    
    /**
     * Get all order items for a specific student (paginated).
     */
    Page<OrderItemResponseDTO> getOrderItemsByStudentIdPaginated(String studentId, Pageable pageable);
    
    /**
     * Get all order items for a specific student with optional status filter (paginated).
     */
    Page<OrderItemResponseDTO> getOrderItemsByStudentIdAndStatusPaginated(String studentId, OrderStatus status, Pageable pageable);

    /**
     * Update order item status.
     */
    OrderItemResponseDTO updateOrderItemStatus(Long id, OrderStatus status);


    
    /**
     * Delete order item.
     */
    void deleteOrderItem(Long id);
}
