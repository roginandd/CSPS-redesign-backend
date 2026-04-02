package org.csps.backend.controller;

import java.util.List;

import org.csps.backend.annotation.Auditable;
import org.csps.backend.domain.dtos.request.MerchVariantItemRequestDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.dtos.response.MerchVariantItemResponseDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieConfigResponseDTO;
import org.csps.backend.domain.enums.AuditAction;
import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.service.MerchVariantItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/merch-variant-item")
@RequiredArgsConstructor
public class MerchVariantItemController {

    private final MerchVariantItemService merchVariantItemService;

    /**
     * Add a single item to a merch variant.
     * Only admins can add items.
     */
    @PostMapping("/{merchVariantId}/add")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "MerchVariantItem")
    public ResponseEntity<GlobalResponseBuilder<MerchVariantItemResponseDTO>> addItemToVariant(
            @PathVariable Long merchVariantId,
            @Valid @RequestBody MerchVariantItemRequestDTO requestDTO) {
        MerchVariantItemResponseDTO responseDTO = merchVariantItemService.addItemToVariant(merchVariantId, requestDTO);
        return GlobalResponseBuilder.buildResponse("Merch variant item added successfully", responseDTO, HttpStatus.CREATED);
    }

    /**
     * Add multiple items to a merch variant.
     * Only admins can add items.
     */
    @PostMapping("/{merchVariantId}/add-multiple")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "MerchVariantItem")
    public ResponseEntity<GlobalResponseBuilder<List<MerchVariantItemResponseDTO>>> addMultipleItemsToVariant(
            @PathVariable Long merchVariantId,
            @Valid @RequestBody List<MerchVariantItemRequestDTO> requestDTOs) {
        List<MerchVariantItemResponseDTO> responseDTOs = merchVariantItemService.addMultipleItemsToVariant(merchVariantId, requestDTOs);
        return GlobalResponseBuilder.buildResponse("Merch variant items added successfully", responseDTOs, HttpStatus.CREATED);
    }

    /**
     * Get merch variant item by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<MerchVariantItemResponseDTO>> getItemById(
            @PathVariable Long id) {
        MerchVariantItemResponseDTO responseDTO = merchVariantItemService.getItemById(id);
        return GlobalResponseBuilder.buildResponse("Merch variant item retrieved successfully", responseDTO, HttpStatus.OK);
    }

    /**
     * Get all freebies configured for the ticket owning this merch variant item.
     */
    @GetMapping("/{id}/freebies")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<List<TicketFreebieConfigResponseDTO>>> getFreebiesByMerchVariantItemId(
            @PathVariable Long id) {
        List<TicketFreebieConfigResponseDTO> responseDTOs = merchVariantItemService.getFreebiesByMerchVariantItemId(id);
        return GlobalResponseBuilder.buildResponse("Merch variant item freebies retrieved successfully", responseDTOs, HttpStatus.OK);
    }

    /**
     * Get all items for a specific merch variant.
     */
    @GetMapping("/variant/{merchVariantId}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<List<MerchVariantItemResponseDTO>>> getItemsByVariantId(
            @PathVariable Long merchVariantId) {
        List<MerchVariantItemResponseDTO> responseDTOs = merchVariantItemService.getItemsByVariantId(merchVariantId);
        return GlobalResponseBuilder.buildResponse("Merch variant items retrieved successfully", responseDTOs, HttpStatus.OK);
    }

    /**
     * Get item by variant ID and size.
     */
    @GetMapping("/variant/{merchVariantId}/size/{size}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<MerchVariantItemResponseDTO>> getItemByVariantAndSize(
            @PathVariable Long merchVariantId,
            @PathVariable ClothingSizing size) {
        MerchVariantItemResponseDTO responseDTO = merchVariantItemService.getItemByVariantAndSize(merchVariantId, size);
        return GlobalResponseBuilder.buildResponse("Merch variant item retrieved successfully", responseDTO, HttpStatus.OK);
    }

    /**
     * Update stock quantity for a merch variant item.
     * Only admins can update stock.
     */
    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "MerchVariantItem")
    public ResponseEntity<GlobalResponseBuilder<MerchVariantItemResponseDTO>> updateStockQuantity(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        MerchVariantItemResponseDTO responseDTO = merchVariantItemService.updateStockQuantity(id, quantity);
        return GlobalResponseBuilder.buildResponse("Stock quantity updated successfully", responseDTO, HttpStatus.OK);
    }

    /**
     * Update price for a merch variant item.
     * Only admins can update price.
     */
    @PatchMapping("/{id}/price")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "MerchVariantItem")
    public ResponseEntity<GlobalResponseBuilder<MerchVariantItemResponseDTO>> updatePrice(
            @PathVariable Long id,
            @RequestParam Double price) {
        MerchVariantItemResponseDTO responseDTO = merchVariantItemService.updatePrice(id, price);
        return GlobalResponseBuilder.buildResponse("Price updated successfully", responseDTO, HttpStatus.OK);
    }

    /**
     * Delete a merch variant item.
     * Only admins can delete items.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.DELETE, resourceType = "MerchVariantItem")
    public ResponseEntity<GlobalResponseBuilder<Void>> deleteItem(@PathVariable Long id) {
        merchVariantItemService.deleteItem(id);
        return GlobalResponseBuilder.buildResponse("Merch variant item deleted successfully", null, HttpStatus.NO_CONTENT);
    }
}
