package org.csps.backend.domain.entities;

import java.time.LocalDateTime;

import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.TicketFreebieFulfillmentStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "ticket_freebie_assignment",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_freebie_assignment_order_item_config", columnNames = {"order_item_id", "ticket_freebie_config_id"})
    },
    indexes = {
        @Index(name = "idx_ticket_freebie_assignment_order_item", columnList = "order_item_id"),
        @Index(name = "idx_ticket_freebie_assignment_config", columnList = "ticket_freebie_config_id"),
        @Index(name = "idx_ticket_freebie_assignment_status", columnList = "fulfillment_status")
    }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketFreebieAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketFreebieAssignmentId;

    @ManyToOne
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne
    @JoinColumn(name = "ticket_freebie_config_id", nullable = false)
    private TicketFreebieConfig ticketFreebieConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ClothingSizing selectedSize;

    @Column(nullable = true)
    private String selectedColor;

    @Column(nullable = true)
    private String selectedDesign;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false)
    private TicketFreebieFulfillmentStatus fulfillmentStatus;

    @Column(nullable = true)
    private LocalDateTime claimedAt;

    @Column(nullable = true)
    private LocalDateTime fulfilledAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
