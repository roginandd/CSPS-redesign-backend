package org.csps.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.merchVariantItem mvi LEFT JOIN FETCH mvi.merchVariant mv LEFT JOIN FETCH mv.merch LEFT JOIN FETCH o.student s LEFT JOIN FETCH s.userAccount ua LEFT JOIN FETCH ua.userProfile WHERE o.student.studentId = :studentId")
    List<Order> findByStudentId(String studentId);
    
    @EntityGraph(value = "Order.withItemsAndDetails", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT o FROM Order o WHERE o.student.studentId = :studentId ORDER BY o.orderDate DESC")
    Page<Order> findByStudentId(String studentId, Pageable pageable);

    @EntityGraph(value = "Order.withItemsAndDetails", type = EntityGraph.EntityGraphType.FETCH)
    Optional<Order> findByOrderIdAndStudentStudentId(Long orderId, String studentId);

    @EntityGraph(value = "Order.withItemsAndDetails", type = EntityGraph.EntityGraphType.FETCH)
    Page<Order> findAllByOrderByOrderDateDesc(Pageable pageable);
    
    /* efficient paginated query for transactions with eager loading to prevent N+1 queries */
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile", "orderItems", "orderItems.merchVariantItem", "orderItems.merchVariantItem.merchVariant", "orderItems.merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<Order> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile", "orderItems", "orderItems.merchVariantItem", "orderItems.merchVariantItem.merchVariant", "orderItems.merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);
    
    List<Order> findByOrderDateBetween(LocalDateTime start, LocalDateTime end);
    
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    List<Order> findByOrderDateBetweenAndOrderStatus(LocalDateTime start, LocalDateTime end, OrderStatus status);
    
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    List<Order> findByOrderStatus(OrderStatus status);
}

    
