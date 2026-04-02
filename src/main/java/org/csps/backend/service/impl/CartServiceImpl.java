package org.csps.backend.service.impl;

import org.csps.backend.domain.dtos.response.CartResponseDTO;
import org.csps.backend.domain.entities.Cart;
import org.csps.backend.exception.CartNotFoundException;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.mapper.CartMapper;
import org.csps.backend.repository.CartRepository;
import org.csps.backend.service.CartItemService;
import org.csps.backend.service.CartService;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartMapper cartMapper;
    private final CartItemService cartItemService;

    @Override
    public CartResponseDTO getCartByStudentId(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        Cart cart = cartRepository.findByIdWithItems(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for student: " + studentId));

        CartResponseDTO response = cartMapper.toResponseDTO(cart);
        response.setCartItemResponseDTOs(cartItemService.getCartItems(studentId));
        return response;
    }

    @Override
    @Transactional
    public Cart createCart(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID cannot be null or empty");
        }
        
        // Check if cart already exists
        if (cartRepository.existsById(studentId)) {
            throw new InvalidRequestException("Cart already exists for this student");
        }
        
        Cart cart = Cart.builder()
                .cartId(studentId)
                .build();
        return cartRepository.save(cart);
    }

    @Override
    public Double getCartTotal(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        Cart cart = cartRepository.findByIdWithItems(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));

        return cart.getItems().stream()
                .mapToDouble(item -> item.getQuantity() * item.getMerchVariantItem().getPrice())
                .sum();
    }

    @Override
    public int getCartItemCount(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        Cart cart = cartRepository.findByIdWithItems(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));

        return cart.getItems().stream()
                .mapToInt(item -> item.getQuantity())
                .sum();
    }

    @Override
    @Transactional
    public void clearCart(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
        
        Cart cart = cartRepository.findByIdWithItems(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));

        cart.getItems().clear();
        cartRepository.save(cart);
    }
}
