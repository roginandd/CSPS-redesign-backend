package org.csps.backend.controller;

import java.util.List;

import org.csps.backend.domain.dtos.request.CartItemRequestDTO;
import org.csps.backend.domain.dtos.response.CartItemResponseDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.service.CartItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cart-items")
@RequiredArgsConstructor
public class CartItemController {

    private final CartItemService cartItemService;

    /**
     * Add item to cart.
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<CartItemResponseDTO>> addCartItem(
            @AuthenticationPrincipal String studentId,
            @Valid @RequestBody CartItemRequestDTO requestDTO) {
        CartItemResponseDTO responseDTO = cartItemService.addCartItem(studentId, requestDTO);
        return GlobalResponseBuilder.buildResponse("Cart item added successfully", responseDTO, HttpStatus.CREATED);
    }

    /**
     * Get all items in cart.
     */
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<List<CartItemResponseDTO>>> getCartItems(
            @AuthenticationPrincipal String studentId) {
        List<CartItemResponseDTO> responseDTOs = cartItemService.getCartItems(studentId);
        return GlobalResponseBuilder.buildResponse("Cart items retrieved successfully", responseDTOs, HttpStatus.OK);
    }

    /**
     * Edit a cart item by updating its quantity.
     */
    @PutMapping("/{merchVariantItemId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<CartItemResponseDTO>> updateCartItemQuantity(
            @AuthenticationPrincipal String studentId,
            @PathVariable Long merchVariantItemId,
            @RequestParam int quantity) {
        CartItemResponseDTO responseDTO = cartItemService.updateCartItemQuantity(studentId, merchVariantItemId, quantity);
        return GlobalResponseBuilder.buildResponse("Cart item updated successfully", responseDTO, HttpStatus.OK);
    }

    /**
     * Remove item from cart.
     */
    @DeleteMapping("/{merchVariantItemId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GlobalResponseBuilder<Void>> removeCartItem(
            @AuthenticationPrincipal String studentId,
            @PathVariable Long merchVariantItemId) {
        cartItemService.removeCartItem(studentId, merchVariantItemId);
        return GlobalResponseBuilder.buildResponse("Cart item removed successfully", null, HttpStatus.NO_CONTENT);
    }
}
