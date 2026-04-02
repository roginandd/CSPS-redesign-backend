package org.csps.backend.domain.entities;

import org.csps.backend.domain.enums.ClothingSizing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "cart_item_freebie_selection",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_item_freebie_selection_cart_config", columnNames = {"student_id", "merch_variant_item_id", "ticket_freebie_config_id"})
    },
    indexes = {
        @Index(name = "idx_cart_item_freebie_selection_cart", columnList = "student_id, merch_variant_item_id"),
        @Index(name = "idx_cart_item_freebie_selection_config", columnList = "ticket_freebie_config_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemFreebieSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cartItemFreebieSelectionId;

    @ManyToOne
    @JoinColumns({
        @JoinColumn(name = "student_id", referencedColumnName = "student_id", nullable = false),
        @JoinColumn(name = "merch_variant_item_id", referencedColumnName = "merch_variant_item_id", nullable = false)
    })
    private CartItem cartItem;

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
}
