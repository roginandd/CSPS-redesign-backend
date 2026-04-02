package org.csps.backend.domain.entities;

import java.util.ArrayList;
import java.util.List;

import org.csps.backend.domain.entities.composites.CartItemId;
import org.csps.backend.domain.enums.ClothingSizing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @EmbeddedId
    private CartItemId cartItemId;

    @ManyToOne
    @MapsId("cartId")
    @JoinColumn(name = "student_id", nullable = false)
    private Cart cart;

    @ManyToOne
    @MapsId("merchVariantItemId")
    @JoinColumn(name = "merch_variant_item_id", nullable = false)
    private MerchVariantItem merchVariantItem;

    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ClothingSizing selectedFreebieSize;

    @Column(nullable = true)
    private String selectedFreebieColor;

    @Column(nullable = true)
    private String selectedFreebieDesign;

    @OneToMany(mappedBy = "cartItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItemFreebieSelection> freebieSelections = new ArrayList<>();
}
