package org.csps.backend.service;

import java.util.List;

import org.csps.backend.domain.dtos.request.MerchVariantItemRequestDTO;
import org.csps.backend.domain.dtos.response.MerchVariantItemResponseDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieConfigResponseDTO;
import org.csps.backend.domain.enums.ClothingSizing;

public interface MerchVariantItemService {
    
    /**
     * Add a size/stock variant to an existing MerchVariant (SKU-level).
     * Used for clothing and other products with size variants.
     */
    MerchVariantItemResponseDTO addItemToVariant(Long merchVariantId, MerchVariantItemRequestDTO dto);
    
    /**
     * Add multiple items to a variant in a single operation.
     */
    List<MerchVariantItemResponseDTO> addMultipleItemsToVariant(Long merchVariantId, List<MerchVariantItemRequestDTO> dtos);
    
    /**
     * Get all items (SKUs) for a specific variant.
     */
    List<MerchVariantItemResponseDTO> getItemsByVariantId(Long merchVariantId);
    
    /**
     * Get a specific item by size and variant.
     */
    MerchVariantItemResponseDTO getItemByVariantAndSize(Long merchVariantId, ClothingSizing size);
    
    /**
     * Update stock quantity for a specific item.
     */
    MerchVariantItemResponseDTO updateStockQuantity(Long merchVariantItemId, Integer newQuantity);
    
    /**
     * Update price for a specific item.
     */
    MerchVariantItemResponseDTO updatePrice(Long merchVariantItemId, Double newPrice);
    
    /**
     * Delete a specific item.
     */
    void deleteItem(Long merchVariantItemId);
    
    /**
     * Get item by ID.
     */
    MerchVariantItemResponseDTO getItemById(Long merchVariantItemId);

    /**
     * Get all freebie configs available for a merch variant item.
     */
    List<TicketFreebieConfigResponseDTO> getFreebiesByMerchVariantItemId(Long merchVariantItemId);
}
