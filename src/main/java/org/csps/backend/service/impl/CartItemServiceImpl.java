package org.csps.backend.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.csps.backend.domain.dtos.request.CartItemRequestDTO;
import org.csps.backend.domain.dtos.request.TicketFreebieSelectionRequestDTO;
import org.csps.backend.domain.dtos.response.CartItemFreebieSelectionItemResponseDTO;
import org.csps.backend.domain.dtos.response.CartItemFreebieSelectionResponseDTO;
import org.csps.backend.domain.dtos.response.CartItemResponseDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieConfigResponseDTO;
import org.csps.backend.domain.entities.Cart;
import org.csps.backend.domain.entities.CartItem;
import org.csps.backend.domain.entities.CartItemFreebieSelection;
import org.csps.backend.domain.entities.MerchVariantItem;
import org.csps.backend.domain.entities.TicketFreebieConfig;
import org.csps.backend.domain.entities.composites.CartItemId;
import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.domain.enums.TicketFreebieCategory;
import org.csps.backend.exception.CartItemNotFoundException;
import org.csps.backend.exception.CartNotFoundException;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.MerchVariantNotFoundException;
import org.csps.backend.mapper.CartItemMapper;
import org.csps.backend.repository.CartItemRepository;
import org.csps.backend.repository.CartRepository;
import org.csps.backend.repository.MerchVariantItemRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.TicketFreebieConfigRepository;
import org.csps.backend.service.CartItemService;
import org.csps.backend.service.TicketFreebieConfigService;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartItemServiceImpl implements CartItemService {

    private static final String ITEM_ALREADY_ADDED = "ITEM ALREADY ADDED IN THE CART";
    private static final String ITEM_ALREADY_IN_CART_OR_ORDER = "Item is already in the cart / order";

    private final CartItemRepository cartItemRepository;
    private final MerchVariantItemRepository merchVariantItemRepository;
    private final CartRepository cartRepository;
    private final OrderItemRepository orderItemRepository;
    private final TicketFreebieConfigRepository ticketFreebieConfigRepository;
    private final CartItemMapper cartItemMapper;
    private final TicketFreebieConfigService ticketFreebieConfigService;

    @Override
    @Transactional
    public CartItemResponseDTO addCartItem(String studentId, CartItemRequestDTO cartItemRequestDTO) {
        validateStudentId(studentId);
        if (cartItemRequestDTO == null) {
            throw new InvalidRequestException("Cart item request is required");
        }

        Long merchVariantItemId = cartItemRequestDTO.getMerchVariantItemId();
        int quantity = cartItemRequestDTO.getQuantity();
        if (merchVariantItemId == null || merchVariantItemId <= 0) {
            throw new InvalidRequestException("Merch variant item ID is required");
        }
        if (quantity <= 0) {
            throw new InvalidRequestException("Quantity must be greater than 0");
        }

        Cart cart = cartRepository.findById(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));
        MerchVariantItem merchVariantItem = merchVariantItemRepository.findById(merchVariantItemId)
                .orElseThrow(() -> new MerchVariantNotFoundException("Merch variant item not found"));

        if (quantity > merchVariantItem.getStockQuantity()) {
            throw new InvalidRequestException("Insufficient stock. Available: " + merchVariantItem.getStockQuantity()
                    + ", Requested: " + quantity);
        }

        MerchType merchType = merchVariantItem.getMerchVariant().getMerch().getMerchType();
        List<ValidatedCartSelection> normalizedSelections = validateAndNormalizeSelections(
                merchVariantItem,
                cartItemRequestDTO.getFreebieSelections(),
                true);

        if (merchType == MerchType.TICKET || merchType == MerchType.MEMBERSHIP) {
            validateSingleEntryRules(studentId, merchVariantItem, quantity);
            CartItemId cartItemId = new CartItemId(studentId, merchVariantItemId);
            if (merchType == MerchType.MEMBERSHIP && cartItemRepository.existsById(cartItemId)) {
                throw new InvalidRequestException(ITEM_ALREADY_ADDED);
            }

            CartItem cartItem = CartItem.builder()
                    .cartItemId(cartItemId)
                    .cart(cart)
                    .merchVariantItem(merchVariantItem)
                    .quantity(1)
                    .build();
            applySelections(cartItem, normalizedSelections);
            return enrichResponse(cartItemMapper.toResponseDTO(cartItemRepository.save(cartItem)), cartItem);
        }

        CartItemId cartItemId = new CartItemId(studentId, merchVariantItemId);
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElse(CartItem.builder()
                        .cartItemId(cartItemId)
                        .cart(cart)
                        .merchVariantItem(merchVariantItem)
                        .quantity(0)
                        .build());

        int totalQuantity = cartItem.getQuantity() + quantity;
        if (totalQuantity > merchVariantItem.getStockQuantity()) {
            throw new InvalidRequestException("Total quantity (" + totalQuantity + ") exceeds available stock ("
                    + merchVariantItem.getStockQuantity() + ")");
        }

        cartItem.setQuantity(totalQuantity);
        cartItem = cartItemRepository.save(cartItem);
        return enrichResponse(cartItemMapper.toResponseDTO(cartItem), cartItem);
    }

    @Override
    public CartItemResponseDTO getCartItemByMerchVariantItemId(String studentId, Long merchVariantItemId) {
        validateStudentId(studentId);
        if (merchVariantItemId == null || merchVariantItemId <= 0) {
            throw new InvalidRequestException("Merch variant item ID is required");
        }

        CartItem cartItem = getCartItem(studentId, merchVariantItemId);
        return enrichResponse(cartItemMapper.toResponseDTO(cartItem), cartItem);
    }

    @Override
    @Transactional
    public void removeCartItem(String studentId, Long merchVariantItemId) {
        validateStudentId(studentId);
        if (merchVariantItemId == null || merchVariantItemId <= 0) {
            throw new InvalidRequestException("Merch variant item ID is required");
        }

        CartItemId cartItemId = new CartItemId(studentId, merchVariantItemId);
        if (!cartItemRepository.existsById(cartItemId)) {
            throw new CartItemNotFoundException("Cart item not found");
        }
        cartItemRepository.deleteById(cartItemId);
    }

    @Override
    @Transactional
    public CartItemResponseDTO updateCartItemQuantity(String studentId, Long merchVariantItemId, int quantity) {
        validateStudentId(studentId);
        if (merchVariantItemId == null || merchVariantItemId <= 0) {
            throw new InvalidRequestException("Merch variant item ID is required");
        }
        if (quantity < 0) {
            throw new InvalidRequestException("Quantity cannot be negative");
        }

        CartItem cartItem = getCartItem(studentId, merchVariantItemId);
        MerchType merchType = cartItem.getMerchVariantItem().getMerchVariant().getMerch().getMerchType();
        if ((merchType == MerchType.TICKET || merchType == MerchType.MEMBERSHIP) && quantity > 1) {
            throw new InvalidRequestException("Quantity for ticket and membership items must remain 1");
        }

        if (quantity == 0) {
            cartItemRepository.delete(cartItem);
            return null;
        }

        if (quantity > cartItem.getMerchVariantItem().getStockQuantity()) {
            throw new InvalidRequestException("Insufficient stock. Available: "
                    + cartItem.getMerchVariantItem().getStockQuantity() + ", Requested: " + quantity);
        }

        cartItem.setQuantity(quantity);
        cartItem = cartItemRepository.save(cartItem);
        return enrichResponse(cartItemMapper.toResponseDTO(cartItem), cartItem);
    }

    @Override
    @Transactional
    public CartItemFreebieSelectionResponseDTO getCartItemFreebieSelection(String studentId, Long merchVariantItemId) {
        validateStudentId(studentId);
        if (merchVariantItemId == null || merchVariantItemId <= 0) {
            throw new InvalidRequestException("Merch variant item ID is required");
        }

        CartItem cartItem = getCartItem(studentId, merchVariantItemId);
        List<TicketFreebieConfigResponseDTO> configs = getRequiredCartFreebieConfigs(cartItem);
        Map<Long, CartItemFreebieSelection> selectedByConfigId = cartItem.getFreebieSelections().stream()
                .filter(selection -> selection.getTicketFreebieConfig() != null && selection.getTicketFreebieConfig().getTicketFreebieConfigId() != null)
                .collect(Collectors.toMap(selection -> selection.getTicketFreebieConfig().getTicketFreebieConfigId(), selection -> selection));

        return CartItemFreebieSelectionResponseDTO.builder()
                .merchVariantItemId(merchVariantItemId)
                .merchId(cartItem.getMerchVariantItem().getMerchVariant().getMerch().getMerchId())
                .freebies(configs.stream().map(config -> {
                    CartItemFreebieSelection selected = selectedByConfigId.get(config.getTicketFreebieConfigId());
                    return CartItemFreebieSelectionItemResponseDTO.builder()
                            .ticketFreebieConfigId(config.getTicketFreebieConfigId())
                            .displayOrder(config.getDisplayOrder())
                            .category(config.getCategory())
                            .freebieName(config.getFreebieName())
                            .clothingSubtype(config.getClothingSubtype())
                            .availableSizes(config.getSizes())
                            .availableColors(config.getColors())
                            .availableDesigns(config.getDesigns())
                            .selectedSize(selected == null ? null : selected.getSelectedSize())
                            .selectedColor(selected == null ? null : selected.getSelectedColor())
                            .selectedDesign(selected == null ? null : selected.getSelectedDesign())
                            .build();
                }).toList())
                .build();
    }

    @Override
    @Transactional
    public CartItemResponseDTO updateCartItemFreebieSelection(
            String studentId,
            Long merchVariantItemId,
            List<TicketFreebieSelectionRequestDTO> freebieSelections) {
        validateStudentId(studentId);
        if (merchVariantItemId == null || merchVariantItemId <= 0) {
            throw new InvalidRequestException("Merch variant item ID is required");
        }

        CartItem cartItem = getCartItem(studentId, merchVariantItemId);
        List<ValidatedCartSelection> normalizedSelections = validateAndNormalizeSelections(
                cartItem.getMerchVariantItem(),
                freebieSelections,
                true);

        applySelections(cartItem, normalizedSelections);
        CartItem updatedCartItem = cartItemRepository.save(cartItem);
        return enrichResponse(cartItemMapper.toResponseDTO(updatedCartItem), updatedCartItem);
    }

    @Override
    public List<CartItemResponseDTO> getCartItems(String studentId) {
        validateStudentId(studentId);
        Cart cart = cartRepository.findByIdWithItems(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));

        return cart.getItems().stream()
                .map(item -> enrichResponse(cartItemMapper.toResponseDTO(item), item))
                .toList();
    }

    @Override
    @Transactional
    public void clearCart(String studentId) {
        validateStudentId(studentId);
        Cart cart = cartRepository.findById(studentId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private void applySelections(CartItem cartItem, List<ValidatedCartSelection> selections) {
        cartItem.setSelectedFreebieSize(null);
        cartItem.setSelectedFreebieColor(null);
        cartItem.setSelectedFreebieDesign(null);

        Map<Long, CartItemFreebieSelection> existingByConfigId = cartItem.getFreebieSelections().stream()
                .filter(selection -> selection.getTicketFreebieConfig() != null
                        && selection.getTicketFreebieConfig().getTicketFreebieConfigId() != null)
                .collect(Collectors.toMap(
                        selection -> selection.getTicketFreebieConfig().getTicketFreebieConfigId(),
                        selection -> selection,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new));

        Set<Long> retainedConfigIds = selections.stream()
                .map(selection -> selection.config().getTicketFreebieConfigId())
                .collect(Collectors.toSet());

        cartItem.getFreebieSelections().removeIf(existingSelection -> {
            TicketFreebieConfig config = existingSelection.getTicketFreebieConfig();
            return config == null
                    || config.getTicketFreebieConfigId() == null
                    || !retainedConfigIds.contains(config.getTicketFreebieConfigId());
        });

        for (ValidatedCartSelection selection : selections) {
            Long configId = selection.config().getTicketFreebieConfigId();
            CartItemFreebieSelection freebieSelection = existingByConfigId.get(configId);
            if (freebieSelection == null) {
                freebieSelection = CartItemFreebieSelection.builder()
                        .cartItem(cartItem)
                        .build();
                cartItem.getFreebieSelections().add(freebieSelection);
            }

            freebieSelection.setCartItem(cartItem);
            freebieSelection.setTicketFreebieConfig(selection.config());
            freebieSelection.setSelectedSize(selection.selectedSize());
            freebieSelection.setSelectedColor(selection.selectedColor());
            freebieSelection.setSelectedDesign(selection.selectedDesign());
        }
    }

    private void validateSingleEntryRules(String studentId, MerchVariantItem merchVariantItem, int quantity) {
        if (quantity != 1) {
            throw new InvalidRequestException("Quantity for ticket and membership items must be exactly 1");
        }

        MerchType merchType = merchVariantItem.getMerchVariant().getMerch().getMerchType();
        if (merchType == MerchType.TICKET) {
            Long merchId = merchVariantItem.getMerchVariant().getMerch().getMerchId();
            if (cartItemRepository.existsByStudentIdAndMerchId(studentId, merchId)
                    || hasActiveOrderForMerch(studentId, merchId)) {
                throw new InvalidRequestException(ITEM_ALREADY_IN_CART_OR_ORDER);
            }
            return;
        }

        if (cartItemRepository.existsByStudentIdAndMerchType(studentId, merchType)) {
            throw new InvalidRequestException(ITEM_ALREADY_ADDED);
        }
    }

    private boolean hasActiveOrderForMerch(String studentId, Long merchId) {
        return orderItemRepository.existsByStudentIdAndMerchIdAndOrderStatusNotIn(
                studentId,
                merchId,
                List.of(OrderStatus.CANCELLED, OrderStatus.REJECTED));
    }

    private CartItemResponseDTO enrichResponse(CartItemResponseDTO response, CartItem cartItem) {
        MerchVariantItem merchVariantItem = cartItem.getMerchVariantItem();
        if (merchVariantItem == null || merchVariantItem.getMerchVariant() == null
                || merchVariantItem.getMerchVariant().getMerch() == null) {
            return response;
        }

        var merch = merchVariantItem.getMerchVariant().getMerch();
        if (merch.getMerchType() != MerchType.TICKET || !Boolean.TRUE.equals(merch.getHasFreebie())) {
            response.setHasFreebie(false);
            response.setFreebieSelections(List.of());
            return response;
        }

        List<TicketFreebieConfigResponseDTO> configs = ticketFreebieConfigService.getConfigsByTicketMerchId(merch.getMerchId());
        Map<Long, CartItemFreebieSelection> selectedByConfigId = cartItem.getFreebieSelections().stream()
                .filter(selection -> selection.getTicketFreebieConfig() != null && selection.getTicketFreebieConfig().getTicketFreebieConfigId() != null)
                .collect(Collectors.toMap(selection -> selection.getTicketFreebieConfig().getTicketFreebieConfigId(), selection -> selection));

        response.setHasFreebie(!configs.isEmpty());
        response.setFreebieSelections(configs.stream().map(config -> {
            CartItemFreebieSelection selected = selectedByConfigId.get(config.getTicketFreebieConfigId());
            return CartItemFreebieSelectionItemResponseDTO.builder()
                    .ticketFreebieConfigId(config.getTicketFreebieConfigId())
                    .displayOrder(config.getDisplayOrder())
                    .category(config.getCategory())
                    .freebieName(config.getFreebieName())
                    .clothingSubtype(config.getClothingSubtype())
                    .availableSizes(config.getSizes())
                    .availableColors(config.getColors())
                    .availableDesigns(config.getDesigns())
                    .selectedSize(selected == null ? null : selected.getSelectedSize())
                    .selectedColor(selected == null ? null : selected.getSelectedColor())
                    .selectedDesign(selected == null ? null : selected.getSelectedDesign())
                    .build();
        }).toList());
        return response;
    }

    private List<ValidatedCartSelection> validateAndNormalizeSelections(
            MerchVariantItem merchVariantItem,
            List<TicketFreebieSelectionRequestDTO> selections,
            boolean requireCompleteSelections) {
        if (merchVariantItem == null || merchVariantItem.getMerchVariant() == null
                || merchVariantItem.getMerchVariant().getMerch() == null) {
            throw new InvalidRequestException("Merch details are incomplete");
        }

        var merch = merchVariantItem.getMerchVariant().getMerch();
        boolean isTicketWithFreebies = merch.getMerchType() == MerchType.TICKET && Boolean.TRUE.equals(merch.getHasFreebie());
        List<TicketFreebieSelectionRequestDTO> normalizedRequests = selections == null ? List.of() : selections;

        if (!isTicketWithFreebies) {
            if (!normalizedRequests.isEmpty()) {
                throw new InvalidRequestException("Freebie selection is only allowed for ticket merch with freebies");
            }
            return List.of();
        }

        List<TicketFreebieConfig> configs = ticketFreebieConfigRepository.findByTicketMerchMerchIdOrderByDisplayOrderAscTicketFreebieConfigIdAsc(merch.getMerchId());
        if (configs.isEmpty()) {
            throw new InvalidRequestException("Ticket freebie config is missing");
        }

        if (normalizedRequests.isEmpty()) {
            if (requireCompleteSelections) {
                throw new InvalidRequestException("Freebie selections are required before adding this ticket to cart");
            }
            return List.of();
        }

        Map<Long, TicketFreebieSelectionRequestDTO> requestByConfigId = new LinkedHashMap<>();
        for (TicketFreebieSelectionRequestDTO request : normalizedRequests) {
            if (request == null || request.getTicketFreebieConfigId() == null) {
                throw new InvalidRequestException("Each freebie selection must include ticketFreebieConfigId");
            }
            if (requestByConfigId.put(request.getTicketFreebieConfigId(), request) != null) {
                throw new InvalidRequestException("Duplicate freebie selections are not allowed for the same config");
            }
        }

        List<ValidatedCartSelection> normalizedSelections = new ArrayList<>();
        for (TicketFreebieConfig config : configs) {
            TicketFreebieSelectionRequestDTO request = requestByConfigId.remove(config.getTicketFreebieConfigId());
            if (request == null) {
                throw new InvalidRequestException("Missing freebie selection for config " + config.getFreebieName());
            }

            ClothingSizing selectedSize = request.getSelectedSize();
            String selectedColor = normalizeOptionalText(request.getSelectedColor());
            String selectedDesign = normalizeOptionalText(request.getSelectedDesign());

            if (config.getCategory() == TicketFreebieCategory.CLOTHING) {
                validateClothingSelection(config, selectedSize, selectedColor, selectedDesign);
                normalizedSelections.add(new ValidatedCartSelection(config, selectedSize, selectedColor, null));
            } else {
                validateNonClothingSelection(config, selectedSize, selectedColor, selectedDesign);
                normalizedSelections.add(new ValidatedCartSelection(config, null, null, selectedDesign));
            }
        }

        if (!requestByConfigId.isEmpty()) {
            throw new InvalidRequestException("Submitted freebie selections contain unknown configs");
        }
        return normalizedSelections;
    }

    private void validateClothingSelection(
            TicketFreebieConfig config,
            ClothingSizing selectedSize,
            String selectedColor,
            String selectedDesign) {
        if (selectedSize == null || selectedColor == null) {
            throw new InvalidRequestException("Freebie size and color are required for " + config.getFreebieName());
        }
        if (selectedDesign != null) {
            throw new InvalidRequestException("Freebie design is not allowed for clothing freebies");
        }

        Set<ClothingSizing> allowedSizes = config.getSizeOptions().stream().map(option -> option.getSizeLabel()).collect(Collectors.toSet());
        Set<String> allowedColors = config.getColorOptions().stream()
                .map(option -> option.getColorLabel().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (!allowedSizes.contains(selectedSize)) {
            throw new InvalidRequestException("Selected freebie size is not allowed for " + config.getFreebieName());
        }
        if (!allowedColors.contains(selectedColor.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("Selected freebie color is not allowed for " + config.getFreebieName());
        }
    }

    private void validateNonClothingSelection(
            TicketFreebieConfig config,
            ClothingSizing selectedSize,
            String selectedColor,
            String selectedDesign) {
        if (selectedDesign == null) {
            throw new InvalidRequestException("Freebie design is required for " + config.getFreebieName());
        }
        if (selectedSize != null || selectedColor != null) {
            throw new InvalidRequestException("Freebie size and color are not allowed for non-clothing freebies");
        }

        Set<String> allowedDesigns = config.getDesignOptions().stream()
                .map(option -> option.getDesignLabel().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!allowedDesigns.contains(selectedDesign.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("Selected freebie design is not allowed for " + config.getFreebieName());
        }
    }

    private List<TicketFreebieConfigResponseDTO> getRequiredCartFreebieConfigs(CartItem cartItem) {
        if (cartItem == null || cartItem.getMerchVariantItem() == null
                || cartItem.getMerchVariantItem().getMerchVariant() == null
                || cartItem.getMerchVariantItem().getMerchVariant().getMerch() == null) {
            throw new InvalidRequestException("Cart item merch details are incomplete");
        }

        var merch = cartItem.getMerchVariantItem().getMerchVariant().getMerch();
        if (merch.getMerchType() != MerchType.TICKET || !Boolean.TRUE.equals(merch.getHasFreebie())) {
            throw new InvalidRequestException("This cart item does not support freebies");
        }

        List<TicketFreebieConfigResponseDTO> configs = ticketFreebieConfigService.getConfigsByTicketMerchId(merch.getMerchId());
        if (configs.isEmpty()) {
            throw new InvalidRequestException("Ticket freebie config is missing");
        }
        return configs;
    }

    private CartItem getCartItem(String studentId, Long merchVariantItemId) {
        return cartItemRepository.findById(new CartItemId(studentId, merchVariantItemId))
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found"));
    }

    private void validateStudentId(String studentId) {
        if (studentId == null || studentId.isEmpty()) {
            throw new InvalidRequestException("Student ID is required");
        }
    }

    private String normalizeOptionalText(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private record ValidatedCartSelection(
            TicketFreebieConfig config,
            ClothingSizing selectedSize,
            String selectedColor,
            String selectedDesign) {
    }
}
