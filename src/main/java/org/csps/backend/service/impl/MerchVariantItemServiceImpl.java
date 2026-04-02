package org.csps.backend.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.csps.backend.domain.dtos.request.MerchVariantItemRequestDTO;
import org.csps.backend.domain.dtos.response.MerchVariantItemResponseDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieConfigResponseDTO;
import org.csps.backend.domain.entities.Merch;
import org.csps.backend.domain.entities.MerchVariant;
import org.csps.backend.domain.entities.MerchVariantItem;
import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.MerchVariantNotFoundException;
import org.csps.backend.mapper.MerchVariantItemMapper;
import org.csps.backend.repository.CartItemRepository;
import org.csps.backend.repository.MerchVariantItemRepository;
import org.csps.backend.repository.MerchVariantRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.service.MerchVariantItemService;
import org.csps.backend.service.TicketFreebieConfigService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MerchVariantItemServiceImpl implements MerchVariantItemService {

    private final MerchVariantItemRepository itemRepository;
    private final MerchVariantRepository variantRepository;
    private final MerchVariantItemMapper itemMapper;
    private final TicketFreebieConfigService ticketFreebieConfigService;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional
    public MerchVariantItemResponseDTO addItemToVariant(Long merchVariantId, MerchVariantItemRequestDTO dto) {
        // Validate input
        if (dto == null) {
            throw new InvalidRequestException("MerchVariantItemRequestDTO is required");
        }
        if (dto.getPrice() == null || dto.getPrice() < 0) {
            throw new InvalidRequestException("Price is required and must be non-negative");
        }
        if (dto.getStockQuantity() == null || dto.getStockQuantity() < 0) {
            throw new InvalidRequestException("Stock quantity is required and must be non-negative");
        }

        // Fetch the parent variant
        MerchVariant variant = variantRepository.findById(merchVariantId)
                .orElseThrow(() -> new MerchVariantNotFoundException("MerchVariant not found with id: " + merchVariantId));

        // Get the merch type from the variant's parent merch
        Merch merch = variant.getMerch();
        MerchType merchType = merch.getMerchType();

        // Validate based on merch type
        if (merchType == MerchType.CLOTHING) {
            // For clothing: size is REQUIRED, no color/design in items
            if (dto.getSize() == null) {
                throw new InvalidRequestException("Size is required for clothing merchandise items");
            }
            
            boolean exists = itemRepository.existsByMerchVariantAndSize(variant, dto.getSize());
            if (exists) {
                throw new InvalidRequestException("Item with size " + dto.getSize() + " already exists for this variant");
            }
        } else {
            // For non-clothing (PIN, STICKER, KEYCHAIN, etc): size NOT allowed, must use design
            if (dto.getSize() != null) {
                throw new InvalidRequestException("Size should not be provided for non-clothing merchandise types");
            }
            // Non-clothing variants must use design, not color
            if (variant.getColor() != null && !variant.getColor().isEmpty()) {
                throw new InvalidRequestException("Non-clothing merchandise must use design, not color");
            }
        }

        // Create and save the item
        MerchVariantItem item = MerchVariantItem.builder()
                .merchVariant(variant)
                .size(dto.getSize())
                .price(dto.getPrice())
                .stockQuantity(dto.getStockQuantity())
                .build();

        MerchVariantItem saved = itemRepository.save(item);
        return itemMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public List<MerchVariantItemResponseDTO> addMultipleItemsToVariant(Long merchVariantId, List<MerchVariantItemRequestDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new InvalidRequestException("At least one item must be provided");
        }

        // Fetch the parent variant
        MerchVariant variant = variantRepository.findById(merchVariantId)
                .orElseThrow(() -> new MerchVariantNotFoundException("MerchVariant not found with id: " + merchVariantId));

        // Get the merch type from the variant's parent merch
        Merch merch = variant.getMerch();
        MerchType merchType = merch.getMerchType();
        
        // batch fetch existing items for this variant to avoid N queries in loop
        List<MerchVariantItem> existingItems = itemRepository.findByMerchVariantMerchVariantId(merchVariantId);
        java.util.Set<ClothingSizing> existingSizesSet = existingItems.stream()
                .map(MerchVariantItem::getSize)
                .collect(java.util.stream.Collectors.toSet());

        List<MerchVariantItem> itemsToSave = new ArrayList<>();

        for (MerchVariantItemRequestDTO dto : dtos) {
            // Validate each item
            if (dto == null) {
                throw new InvalidRequestException("MerchVariantItemRequestDTO cannot be null");
            }
            if (dto.getPrice() == null || dto.getPrice() < 0) {
                throw new InvalidRequestException("Price is required and must be non-negative");
            }
            if (dto.getStockQuantity() == null || dto.getStockQuantity() < 0) {
                throw new InvalidRequestException("Stock quantity is required and must be non-negative");
            }

            // Validate based on merch type
            if (merchType == MerchType.CLOTHING) {
                // For clothing: size is REQUIRED, no color/design in items
                if (dto.getSize() == null) {
                    throw new InvalidRequestException("Size is required for clothing merchandise items");
                }
                if (variant.getDesign() != null && !variant.getDesign().isEmpty()) {
                    throw new InvalidRequestException("Design should not be provided for clothing merchandise items");
                }
                
                // check against in-memory set instead of database query
                if (existingSizesSet.contains(dto.getSize())) {
                    throw new InvalidRequestException("Item with size " + dto.getSize() + " already exists for this variant");
                }
                existingSizesSet.add(dto.getSize());
            } else {
                // For non-clothing (PIN, STICKER, KEYCHAIN, etc): size NOT allowed, must use design
                if (dto.getSize() != null) {
                    throw new InvalidRequestException("Size should not be provided for non-clothing merchandise types");
                }
                // Non-clothing variants must use design, not color
                if (variant.getColor() != null && !variant.getColor().isEmpty()) {
                    throw new InvalidRequestException("Non-clothing merchandise must use design, not color");
                }
            }
            
            // Create the item (don't save yet)
            MerchVariantItem item = MerchVariantItem.builder()
                    .merchVariant(variant)
                    .size(dto.getSize())
                    .price(dto.getPrice())
                    .stockQuantity(dto.getStockQuantity())
                    .build();

            itemsToSave.add(item);
        }

        // Batch save all items in a single query
        List<MerchVariantItem> saved = itemRepository.saveAll(itemsToSave);
        
        return saved.stream()
                .map(itemMapper::toResponseDto)
                .toList();
    }

    @Override
    public List<MerchVariantItemResponseDTO> getItemsByVariantId(Long merchVariantId) {
        // Verify variant exists
        if (!variantRepository.existsById(merchVariantId)) {
            throw new MerchVariantNotFoundException("MerchVariant not found with id: " + merchVariantId);
        }

        return itemRepository.findByMerchVariantMerchVariantId(merchVariantId).stream()
                .map(itemMapper::toResponseDto)
                .toList();
    }

    @Override
    public MerchVariantItemResponseDTO getItemByVariantAndSize(Long merchVariantId, ClothingSizing size) {
        // Verify variant exists
        MerchVariant variant = variantRepository.findById(merchVariantId)
                .orElseThrow(() -> new MerchVariantNotFoundException("MerchVariant not found with id: " + merchVariantId));

        MerchVariantItem item = itemRepository.findByMerchVariantAndSize(variant, size)
                .orElseThrow(() -> new InvalidRequestException("Item with size " + size + " not found for this variant"));

        return itemMapper.toResponseDto(item);
    }

    @Override
    @Transactional
    public MerchVariantItemResponseDTO updateStockQuantity(Long merchVariantItemId, Integer newQuantity) {
        if (newQuantity == null || newQuantity < 0) {
            throw new InvalidRequestException("Stock quantity must be non-negative");
        }

        MerchVariantItem item = itemRepository.findById(merchVariantItemId)
                .orElseThrow(() -> new InvalidRequestException("MerchVariantItem not found with id: " + merchVariantItemId));

        item.setStockQuantity(newQuantity);
        MerchVariantItem updated = itemRepository.save(item);
        return itemMapper.toResponseDto(updated);
    }

    @Override
    @Transactional
    public MerchVariantItemResponseDTO updatePrice(Long merchVariantItemId, Double newPrice) {
        if (newPrice == null || newPrice < 0) {
            throw new InvalidRequestException("Price must be non-negative");
        }

        MerchVariantItem item = itemRepository.findById(merchVariantItemId)
                .orElseThrow(() -> new InvalidRequestException("MerchVariantItem not found with id: " + merchVariantItemId));

        item.setPrice(newPrice);
        MerchVariantItem updated = itemRepository.save(item);
        return itemMapper.toResponseDto(updated);
    }


    @Override
    @Transactional
    public void deleteItem(Long merchVariantItemId) {
        MerchVariantItem item = itemRepository.findById(merchVariantItemId)
                .orElseThrow(() -> new InvalidRequestException("MerchVariantItem not found with id: " + merchVariantItemId));

        if (cartItemRepository.existsByMerchVariantItemMerchVariantItemId(merchVariantItemId)) {
            throw new InvalidRequestException("Cannot delete item that is referenced by cart items");
        }
        if (orderItemRepository.existsByMerchVariantItemMerchVariantItemId(merchVariantItemId)) {
            throw new InvalidRequestException("Cannot delete item that is referenced by order items");
        }

        itemRepository.delete(item);
    }

    @Override
    public MerchVariantItemResponseDTO getItemById(Long merchVariantItemId) {
        MerchVariantItem item = itemRepository.findById(merchVariantItemId)
                .orElseThrow(() -> new InvalidRequestException("MerchVariantItem not found with id: " + merchVariantItemId));

        return itemMapper.toResponseDto(item);
    }

    @Override
    public List<TicketFreebieConfigResponseDTO> getFreebiesByMerchVariantItemId(Long merchVariantItemId) {
        return ticketFreebieConfigService.getConfigsByMerchVariantItemId(merchVariantItemId);
    }
}
