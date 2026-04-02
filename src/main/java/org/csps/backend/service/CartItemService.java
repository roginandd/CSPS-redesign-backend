package org.csps.backend.service;

import java.util.List;

import org.csps.backend.domain.dtos.request.CartItemRequestDTO;
import org.csps.backend.domain.dtos.request.TicketFreebieSelectionRequestDTO;
import org.csps.backend.domain.dtos.response.CartItemFreebieSelectionResponseDTO;
import org.csps.backend.domain.dtos.response.CartItemResponseDTO;

public interface CartItemService {

    /**
     * Add item to cart.
     */
    CartItemResponseDTO addCartItem(String studentId, CartItemRequestDTO cartItemRequestDTO);

    /**
     * Get one cart line by merch variant item id.
     */
    CartItemResponseDTO getCartItemByMerchVariantItemId(String studentId, Long merchVariantItemId);

    /**
     * Remove item from cart.
     */
    void removeCartItem(String studentId, Long merchVariantItemId);

    /**
     * Update cart item quantity.
     */
    CartItemResponseDTO updateCartItemQuantity(String studentId, Long merchVariantItemId, int quantity);

    /**
     * Get the available freebie options and current selection for one cart item.
     */
    CartItemFreebieSelectionResponseDTO getCartItemFreebieSelection(
            String studentId,
            Long merchVariantItemId);

    /**
     * Update the selected freebie values for an existing cart item.
     */
    CartItemResponseDTO updateCartItemFreebieSelection(
            String studentId,
            Long merchVariantItemId,
            List<TicketFreebieSelectionRequestDTO> freebieSelections);

    /**
     * Get all items in student's cart.
     */
    List<CartItemResponseDTO> getCartItems(String studentId);

    /**
     * Clear entire cart.
     */
    void clearCart(String studentId);
}
