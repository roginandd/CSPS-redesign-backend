package org.csps.backend.repository;

import java.util.List;
import java.util.Optional;

import org.csps.backend.domain.entities.OrderItem;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    List<OrderItem> findByOrderOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Optional<OrderItem> findByOrderItemIdAndOrderStudentStudentId(Long orderItemId, String studentId);

    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    List<OrderItem> findByOrderOrderIdAndOrderStudentStudentId(Long orderId, String studentId);
    
    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<OrderItem> findByOrderOrderId(Long orderId, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<OrderItem> findByOrderOrderIdAndOrderStudentStudentId(Long orderId, String studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<OrderItem> findByOrderStatusAndOrderStudentStudentId(OrderStatus status, String studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<OrderItem> findByOrderStatusOrderByUpdatedAtDesc(OrderStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<OrderItem> findByOrderStudentStudentIdOrderByUpdatedAtDesc(String studentId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<OrderItem> findByOrderStudentStudentIdAndOrderStatusOrderByUpdatedAtDesc(String studentId, OrderStatus status, Pageable pageable);
    
    /* eager load related entities to prevent N+1 queries in dashboard */
    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    List<OrderItem> findTop5ByOrderStatusInOrderByCreatedAtDesc(List<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    List<OrderItem> findTop5ByOrderStatusInAndMerchVariantItemMerchVariantMerchMerchTypeNotOrderByCreatedAtDesc(
            List<OrderStatus> statuses,
            MerchType merchType);
    
    /* eagerly load order item with student profile and merch details for notifications */
    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT oi FROM OrderItem oi WHERE oi.orderItemId = :id")
    Optional<OrderItem> findByIdWithStudentAndMerchDetails(@Param("id") Long id);

    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    List<OrderItem> findByOrderItemIdIn(List<Long> orderItemIds);

    /**
     * Find all order items for a specific merch (paginated) with eager loading.
     * Used by admin to see which customers purchased a particular merch product.
     *
     * @param merchId the merch ID to filter by
     * @param pageable pagination details
     * @return paginated list of order items for the specified merch
     */
    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT oi FROM OrderItem oi WHERE oi.merchVariantItem.merchVariant.merch.merchId = :merchId")
    Page<OrderItem> findByMerchId(@Param("merchId") Long merchId, Pageable pageable);

    /**
     * Find all order items for a specific merch filtered by order status (paginated).
     * Allows admin to filter merch customers by status (e.g., only CLAIMED orders).
     *
     * @param merchId the merch ID to filter by
     * @param status  the order status to filter by
     * @param pageable pagination details
     * @return paginated list of order items matching merch and status
     */
    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT oi FROM OrderItem oi WHERE oi.merchVariantItem.merchVariant.merch.merchId = :merchId AND oi.orderStatus = :status")
    Page<OrderItem> findByMerchIdAndOrderStatus(@Param("merchId") Long merchId, @Param("status") OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT oi FROM OrderItem oi WHERE oi.merchVariantItem.merchVariant.merch.merchId = :merchId AND oi.orderStatus = :status")
    List<OrderItem> findAllByMerchIdAndOrderStatus(@Param("merchId") Long merchId, @Param("status") OrderStatus status);

    /**
     * Check if a student has any order items of a given merch type with a specific status.
     * Used to check if MEMBERSHIP merch is already in a PENDING order, preventing
     * duplicate membership purchases.
     *
     * @param studentId the student ID
     * @param merchType the merch type to check (e.g., MEMBERSHIP)
     * @param status    the order status to check (e.g., PENDING)
     * @return true if at least one matching order item exists
     */
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi " +
           "WHERE oi.order.student.studentId = :studentId " +
           "AND oi.merchVariantItem.merchVariant.merch.merchType = :merchType " +
           "AND oi.orderStatus = :status")
    boolean existsByStudentIdAndMerchTypeAndStatus(
        @Param("studentId") String studentId,
        @Param("merchType") MerchType merchType,
        @Param("status") OrderStatus status);

    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi " +
           "WHERE oi.order.student.studentId = :studentId " +
           "AND oi.merchVariantItem.merchVariant.merch.merchId = :merchId " +
           "AND oi.orderStatus NOT IN :excludedStatuses")
    boolean existsByStudentIdAndMerchIdAndOrderStatusNotIn(
        @Param("studentId") String studentId,
        @Param("merchId") Long merchId,
        @Param("excludedStatuses") List<OrderStatus> excludedStatuses);

    /**
     * Count total customers (order items) for a specific merch.
     * Used for quick summary without loading full page data.
     *
     * @param merchId the merch ID to count for
     * @return total number of order items for this merch
     */
    @Query("SELECT COUNT(oi) FROM OrderItem oi WHERE oi.merchVariantItem.merchVariant.merch.merchId = :merchId")
    long countByMerchId(@Param("merchId") Long merchId);

    /* check if any order items reference merch variant items */
    boolean existsByMerchVariantItemMerchVariantMerchVariantId(Long merchVariantId);

    boolean existsByMerchVariantItemMerchVariantItemId(Long merchVariantItemId);
    
    /* check if any order items reference a specific merch */
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.merchVariantItem.merchVariant.merch.merchId = :merchId")
    boolean existsByMerch(@Param("merchId") Long merchId);

    /**
     * Find ALL order items for a specific merch (unpaginated) with eager loading.
     * Used for CSV export of merch customer data.
     *
     * @param merchId the merch ID to filter by
     * @return full list of order items for the specified merch
     */
    @EntityGraph(attributePaths = {"order", "order.student", "order.student.userAccount", "order.student.userAccount.userProfile", "merchVariantItem", "merchVariantItem.merchVariant", "merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT oi FROM OrderItem oi WHERE oi.merchVariantItem.merchVariant.merch.merchId = :merchId")
    List<OrderItem> findAllByMerchId(@Param("merchId") Long merchId);
}
