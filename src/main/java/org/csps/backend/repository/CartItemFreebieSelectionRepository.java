package org.csps.backend.repository;

import java.util.List;

import org.csps.backend.domain.entities.CartItemFreebieSelection;
import org.csps.backend.domain.enums.ClothingSizing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemFreebieSelectionRepository extends JpaRepository<CartItemFreebieSelection, Long> {
    List<CartItemFreebieSelection> findByCartItemCartCartIdAndCartItemMerchVariantItemMerchVariantItemId(
            String cartId,
            Long merchVariantItemId);

    boolean existsByTicketFreebieConfigTicketMerchMerchId(Long merchId);

    long countByTicketFreebieConfigTicketFreebieConfigIdAndSelectedSize(Long configId, ClothingSizing selectedSize);

    long countByTicketFreebieConfigTicketFreebieConfigIdAndSelectedColorIgnoreCase(Long configId, String selectedColor);

    long countByTicketFreebieConfigTicketFreebieConfigIdAndSelectedDesignIgnoreCase(Long configId, String selectedDesign);

    void deleteByCartItemCartCartIdAndCartItemMerchVariantItemMerchVariantItemId(String cartId, Long merchVariantItemId);
}
