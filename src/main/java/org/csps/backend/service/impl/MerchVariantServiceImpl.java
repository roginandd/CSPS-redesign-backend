package org.csps.backend.service.impl;

import java.io.IOException;
import java.util.List;

import org.csps.backend.domain.dtos.request.MerchVariantRequestDTO;
import org.csps.backend.domain.dtos.response.MerchVariantResponseDTO;
import org.csps.backend.domain.entities.Merch;
import org.csps.backend.domain.entities.MerchVariant;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.MerchNotFoundException;
import org.csps.backend.exception.MerchVariantAlreadyExisted;
import org.csps.backend.exception.MerchVariantNotFoundException;
import org.csps.backend.mapper.MerchVariantMapper;
import org.csps.backend.repository.MerchRepository;
import org.csps.backend.repository.MerchVariantRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.service.MerchVariantItemService;
import org.csps.backend.service.MerchVariantService;
import org.csps.backend.service.S3Service;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Service implementation for MerchVariant (color/design level).
 * Delegates item-level (size/stock) operations to MerchVariantItemService.
 */
@Service
@RequiredArgsConstructor
public class MerchVariantServiceImpl implements MerchVariantService {

    private final MerchVariantRepository merchVariantRepository;
    private final MerchVariantMapper merchVariantMapper;
    private final MerchRepository merchRepository;
    private final OrderItemRepository orderItemRepository;
    
    private final S3Service s3Service;
    private final MerchVariantItemService merchVariantItemService;

    @Override
    @Transactional
    public MerchVariantResponseDTO addVariantToMerch(Long merchId, MerchVariantRequestDTO dto) throws IOException {
        if (dto == null) {
            throw new InvalidRequestException("MerchVariantRequestDTO is required");
        }

        // Fetch existing merch
        Merch merch = merchRepository.findById(merchId)
                .orElseThrow(() -> new MerchNotFoundException("Merch not found with id: " + merchId));
        
        MerchType merchType = merch.getMerchType();

        // Validate variant based on merch type
        switch (merchType) {
            case CLOTHING -> {
                // For clothing, color is required and must be unique per merch
                if (dto.getColor() == null || dto.getColor().trim().isEmpty()) {
                    throw new InvalidRequestException("color is required for clothing variants");
                }
            
                if (merchVariantRepository.existsByMerchMerchIdAndColor(merchId, dto.getColor())) {
                    throw new MerchVariantAlreadyExisted("Variant with color " + dto.getColor() + " already exists");
                }
            }
            case PIN, STICKER, KEYCHAIN, MEMBERSHIP, TICKET -> {
                // For non-clothing, design is required and must be unique
                if (dto.getDesign() == null || dto.getDesign().trim().isEmpty()) {
                    throw new InvalidRequestException("design is required for this merch type");
                }
                if (merchVariantRepository.existsByMerchMerchIdAndDesign(merchId, dto.getDesign())) {
                    throw new MerchVariantAlreadyExisted("Variant with design " + dto.getDesign() + " already exists");
                }
            }
            default -> throw new InvalidRequestException("Unsupported merch type");
        }

        // Create variant with placeholder
        MerchVariant variant = MerchVariant.builder()
                .merch(merch)
                .color(dto.getColor())
                .design(dto.getDesign())
                .s3ImageKey("placeholder")
                .build();

        // Save variant once (before image upload so we have ID for S3 path)
        MerchVariant saved = merchVariantRepository.save(variant);

        // Upload variant image and update S3 key if provided
        if (dto.getVariantImage() != null && !dto.getVariantImage().isEmpty()) {
            String s3ImageKey = s3Service.uploadFile(dto.getVariantImage(), saved.getMerchVariantId(), "merchVariant");
            saved.setS3ImageKey(s3ImageKey);
            // Update with new S3 key in single save
            saved = merchVariantRepository.save(saved);
        }

        // Add items if provided (batch save handled in service)
        if (dto.getVariantItems() != null && !dto.getVariantItems().isEmpty()) {
            merchVariantItemService.addMultipleItemsToVariant(saved.getMerchVariantId(), dto.getVariantItems());
        }

        return merchVariantMapper.toResponseDTO(saved);
    }

    @Override
    public List<MerchVariantResponseDTO> getAllMerchVariants() {
        return merchVariantRepository.findAll().stream()
                .map(merchVariantMapper::toResponseDTO)
                .toList();
    }

    @Override
    public List<MerchVariantResponseDTO> getVariantsByMerchId(Long merchId) {
        // Verify merch exists
        if (!merchRepository.existsById(merchId)) {
            throw new MerchNotFoundException("Merch not found with id: " + merchId);
        }
        
        return merchVariantRepository.findByMerchMerchId(merchId).stream()
                .map(merchVariantMapper::toResponseDTO)
                .toList();
    }

    @Override
    public MerchVariantResponseDTO getVariantByMerchAndKey(Long merchId, String color, String design) {
        Merch merch = merchRepository.findById(merchId)
                .orElseThrow(() -> new MerchNotFoundException("Merch not found with id: " + merchId));

        MerchType merchType = merch.getMerchType();
        MerchVariant variant;

        if (merchType == MerchType.CLOTHING) {
            if (color == null || color.trim().isEmpty()) {
                throw new InvalidRequestException("color is required for clothing variants");
            }
            variant = merchVariantRepository.findByMerchMerchIdAndColor(merchId, color)
                    .orElseThrow(() -> new MerchVariantNotFoundException("Variant not found with color: " + color));
        } else {
            if (design == null || design.trim().isEmpty()) {
                throw new InvalidRequestException("design is required for this merch type");
            }
            variant = merchVariantRepository.findByMerchMerchIdAndDesign(merchId, design)
                    .orElseThrow(() -> new MerchVariantNotFoundException("Variant not found with design: " + design));
        }

        return merchVariantMapper.toResponseDTO(variant);
    }


    @Override
    @Transactional
    public String uploadVariantImage(Long merchVariantId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Image file is required");
        }

        // Verify variant exists
        MerchVariant variant = merchVariantRepository.findById(merchVariantId)
                .orElseThrow(() -> new MerchVariantNotFoundException("MerchVariant not found with id: " + merchVariantId));
        
        // Delete old image if not placeholder
        if (variant.getS3ImageKey() != null && !variant.getS3ImageKey().isEmpty() && !variant.getS3ImageKey().equals("placeholder")) {
            s3Service.deleteFile(variant.getS3ImageKey());
        }
        
        // Upload new image to S3
        String s3ImageKey = s3Service.uploadFile(file, merchVariantId, "merchVariant");
        
        // Update variant with new S3 key
        variant.setS3ImageKey(s3ImageKey);
        merchVariantRepository.save(variant);
        
        return s3ImageKey;
    }

    @Override
    @Transactional
    public void deleteVariant(Long merchVariantId) {
        // Verify variant exists
        MerchVariant variant = merchVariantRepository.findById(merchVariantId)
                .orElseThrow(() -> new MerchVariantNotFoundException("MerchVariant not found with id: " + merchVariantId));

        /* check if variant items are in any orders; prevent deletion if in use */
        if (orderItemRepository.existsByMerchVariantItemMerchVariantMerchVariantId(merchVariantId)) {
            throw new InvalidRequestException("Cannot delete variant that has items in orders");
        }
        
        merchVariantRepository.delete(variant);

        // Delete S3 image if not placeholder
        if (variant.getS3ImageKey() != null 
            && !variant.getS3ImageKey().isEmpty() 
            && !variant.getS3ImageKey().equals("placeholder")) {
            s3Service.deleteFile(variant.getS3ImageKey());
            // Delete variant (cascades to items)
        }

    }

}


