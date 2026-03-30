package org.csps.backend.service.impl;

import java.util.List;

import org.csps.backend.domain.dtos.request.CartItemRequestDTO;
import org.csps.backend.domain.dtos.response.CartItemResponseDTO;
import org.csps.backend.domain.entities.Cart;
import org.csps.backend.domain.entities.CartItem;
import org.csps.backend.domain.entities.MerchVariantItem;
import org.csps.backend.domain.entities.composites.CartItemId;
import org.csps.backend.exception.CartItemNotFoundException;
import org.csps.backend.exception.CartNotFoundException;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.MerchVariantNotFoundException;
import org.csps.backend.mapper.CartItemMapper;
import org.csps.backend.repository.CartItemRepository;
import org.csps.backend.repository.CartRepository;
import org.csps.backend.repository.MerchVariantItemRepository;
import org.csps.backend.service.CartItemService;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartItemServiceImpl implements CartItemService {

    private final CartItemRepository cartItemRepository;
    private final MerchVariantItemRepository merchVariantItemRepository;
    private final CartRepository cartRepository;
    private final CartItemMapper cartItemMapper;
    
    @Override
    @Transactional
    public CartItemResponseDTO addCartItem(String studentId, CartItemRequestDTO cartItemRequestDTO) {
        if (cartItemRequestDTO == null) {
            throw new InvalidRequestException("Cart item request is required");
        }
        
        String cartId = studentId;
        Long merchVariantItemId = cartItemRequestDTO.getMerchVariantItemId();
        int quantity = cartItemRequestDTO.getQuantity();
        
        if (cartId == null || cartId.isEmpty()) {
            throw new InvalidRequestException("Cart ID is required");
        }
        
        if (merchVariantItemId == null || merchVariantItemId <= 0) {
            throw new InvalidRequestException("Merch variant item ID is required");
        }
        
        if (quantity <= 0) {
            throw new InvalidRequestException("Quantity must be greater than 0");
        }

        // Verify cart exists
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));

        // Verify merch variant item exists
        MerchVariantItem merchVariantItem = merchVariantItemRepository.findById(merchVariantItemId)
                .orElseThrow(() -> new MerchVariantNotFoundException("Merch variant item not found"));

        // Check stock availability
        if (quantity > merchVariantItem.getStockQuantity()) {
            throw new InvalidRequestException("Insufficient stock. Available: " + merchVariantItem.getStockQuantity() + 
                    ", Requested: " + quantity);
        }

        CartItemId cartItemId = new CartItemId(cartId, merchVariantItemId);

        // Check if item already exists in cart
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElse(CartItem.builder()
                        .id(cartItemId)
                        .cart(cart)
                        .merchVariantItem(merchVariantItem)
                        .quantity(0)
                        .build());

        // Validate total quantity doesn't exceed stock
        int totalQuantity = cartItem.getQuantity() + quantity;
        if (totalQuantity > merchVariantItem.getStockQuantity()) {
            throw new InvalidRequestException("Total quantity (" + totalQuantity + ") exceeds available stock (" + 
                    merchVariantItem.getStockQuantity() + ")");
        }

        cartItem.setQuantity(totalQuantity);
        cartItem = cartItemRepository.save(cartItem);

        return cartItemMapper.toResponseDTO(cartItem);
    }

    @Override
    @Transactional
    public void removeCartItem(String studentId, Long merchVariantItemId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        if (merchVariantItemId == null || merchVariantItemId <= 0) {
            throw new InvalidRequestException("Merch variant item ID is required");
        }

        // Get the cart
        Cart cart = cartRepository.findById(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));
        
        // Remove the item from cart's items collection
        CartItemId cartItemId = new CartItemId(studentId, merchVariantItemId);
        boolean removed = cart.getItems().removeIf(item -> item.getId().equals(cartItemId));
        
        if (!removed) {
            throw new CartItemNotFoundException("Cart item not found");
        }
        
        // Save the cart (cascade will handle deletion)
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public CartItemResponseDTO updateCartItemQuantity(String studentId, Long merchVariantItemId, int quantity) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        if (merchVariantItemId == null || merchVariantItemId <= 0) {
            throw new InvalidRequestException("Merch variant item ID is required");
        }
        
        if (quantity < 0) {
            throw new InvalidRequestException("Quantity cannot be negative");
        }

        CartItemId cartItemId = new CartItemId(studentId, merchVariantItemId);
        
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found"));

        // If quantity is 0, delete the item
        if (quantity == 0) {
            cartItemRepository.deleteById(cartItemId);
            return null;
        }

        // Verify stock availability
        MerchVariantItem merchVariantItem = cartItem.getMerchVariantItem();
        if (quantity > merchVariantItem.getStockQuantity()) {
            throw new InvalidRequestException("Insufficient stock. Available: " + merchVariantItem.getStockQuantity() + 
                    ", Requested: " + quantity);
        }

        cartItem.setQuantity(quantity);
        cartItem = cartItemRepository.save(cartItem);

        return cartItemMapper.toResponseDTO(cartItem);
    }

    @Override
    public List<CartItemResponseDTO> getCartItems(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }

        Cart cart = cartRepository.findById(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));

        return cart.getItems().stream()
                .map(cartItemMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    public void clearCart(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }

        Cart cart = cartRepository.findById(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));

        cart.getItems().clear();
        cartRepository.save(cart);
    }
}
