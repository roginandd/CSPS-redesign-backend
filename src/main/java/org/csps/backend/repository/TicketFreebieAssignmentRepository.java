package org.csps.backend.repository;

import java.util.List;

import org.csps.backend.domain.entities.TicketFreebieAssignment;
import org.csps.backend.domain.enums.ClothingSizing;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketFreebieAssignmentRepository extends JpaRepository<TicketFreebieAssignment, Long> {
    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "orderItem.order.student", "orderItem.order.student.userAccount", "orderItem.order.student.userAccount.userProfile", "orderItem.merchVariantItem", "orderItem.merchVariantItem.merchVariant", "orderItem.merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    List<TicketFreebieAssignment> findByOrderItemOrderItemId(Long orderItemId);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "orderItem.order.student", "orderItem.order.student.userAccount", "orderItem.order.student.userAccount.userProfile", "orderItem.merchVariantItem", "orderItem.merchVariantItem.merchVariant", "orderItem.merchVariantItem.merchVariant.merch"}, type = EntityGraph.EntityGraphType.FETCH)
    List<TicketFreebieAssignment> findByOrderItemOrderItemIdIn(List<Long> orderItemIds);

    void deleteByOrderItemOrderItemId(Long orderItemId);

    void deleteByOrderItemOrderItemIdIn(List<Long> orderItemIds);

    @Query("SELECT COUNT(tfa) FROM TicketFreebieAssignment tfa WHERE tfa.ticketFreebieConfig.ticketFreebieConfigId = :configId AND tfa.selectedSize = :size")
    long countByConfigIdAndSelectedSize(@Param("configId") Long configId, @Param("size") ClothingSizing size);

    @Query("SELECT COUNT(tfa) FROM TicketFreebieAssignment tfa WHERE tfa.ticketFreebieConfig.ticketFreebieConfigId = :configId AND LOWER(tfa.selectedColor) = LOWER(:color)")
    long countByConfigIdAndSelectedColor(@Param("configId") Long configId, @Param("color") String color);

    @Query("SELECT COUNT(tfa) FROM TicketFreebieAssignment tfa WHERE tfa.ticketFreebieConfig.ticketFreebieConfigId = :configId AND LOWER(tfa.selectedDesign) = LOWER(:design)")
    long countByConfigIdAndSelectedDesign(@Param("configId") Long configId, @Param("design") String design);
}
