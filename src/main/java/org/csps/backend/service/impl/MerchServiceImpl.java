package org.csps.backend.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.csps.backend.domain.dtos.request.MerchRequestDTO;
import org.csps.backend.domain.dtos.request.MerchUpdateRequestDTO;
import org.csps.backend.domain.dtos.request.MerchVariantItemRequestDTO;
import org.csps.backend.domain.dtos.request.MerchVariantRequestDTO;
import org.csps.backend.domain.dtos.response.MerchDetailedResponseDTO;
import org.csps.backend.domain.dtos.response.MerchSummaryResponseDTO;
import org.csps.backend.domain.entities.Merch;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.MerchAlreadyExistException;
import org.csps.backend.exception.MerchNotFoundException;
import org.csps.backend.mapper.MerchMapper;
import org.csps.backend.repository.CartItemRepository;
import org.csps.backend.repository.MerchRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.StudentMembershipRepository;
import org.csps.backend.service.MerchService;
import org.csps.backend.service.MerchVariantItemService;
import org.csps.backend.service.MerchVariantService;
import org.csps.backend.service.S3Service;
import org.csps.backend.service.StudentService;
import org.csps.backend.service.TicketFreebieConfigService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MerchServiceImpl implements MerchService {

    private static final String ITEM_ALREADY_IN_CART_OR_ORDER = "Item is already in the cart / order";

    private final MerchRepository merchRepository;
    private final MerchMapper merchMapper;
    private final S3Service s3Service;
    private final MerchVariantService merchVariantService;
    private final MerchVariantItemService merchVariantItemService;
    private final StudentMembershipRepository studentMembershipRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final StudentService studentService;
    private final TicketFreebieConfigService ticketFreebieConfigService;

    @Override
    @Transactional
    public MerchDetailedResponseDTO createMerch(MerchRequestDTO request) throws IOException {
        if (request == null) {
            throw new InvalidRequestException("Request is required");
        }

        String merchName = request.getMerchName();
        MerchType merchType = request.getMerchType();
        String description = request.getDescription();
        Double basePrice = request.getBasePrice();

        if (merchName == null || merchName.trim().isEmpty()) {
            throw new InvalidRequestException("Merchandise name is required");
        }
        if (merchType == null) {
            throw new InvalidRequestException("Merchandise type is required");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new InvalidRequestException("Description is required");
        }
        if (basePrice == null || basePrice < 0) {
            throw new InvalidRequestException("Base price is required and must be non-negative");
        }
        if (merchRepository.existsByMerchName(merchName)) {
            throw new MerchAlreadyExistException("Merch name already exists");
        }

        Merch merch = Merch.builder()
                .merchName(merchName)
                .description(description)
                .merchType(merchType)
                .basePrice(basePrice)
                .s3ImageKey("placeholder")
                .hasFreebie(Boolean.TRUE.equals(request.getHasFreebie()))
                .build();

        Merch savedMerch = merchRepository.save(merch);

        validateAndPersistVariants(savedMerch.getMerchId(), merchType, request.getMerchVariantRequestDto());
        ticketFreebieConfigService.syncConfigsForMerch(savedMerch, request.getHasFreebie(), request.getFreebieConfigs());

        if (request.getMerchImage() != null && !request.getMerchImage().isEmpty()) {
            String s3ImageKey = s3Service.uploadFile(request.getMerchImage(), savedMerch.getMerchId(), "merch");
            savedMerch.setS3ImageKey(s3ImageKey);
            savedMerch = merchRepository.save(savedMerch);
        }

        Merch finalMerch = merchRepository.findById(savedMerch.getMerchId())
                .orElseThrow(() -> new MerchNotFoundException("Merch not found"));
        return enrichDetailedResponse(merchMapper.toDetailedResponseDTO(finalMerch));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchDetailedResponseDTO> getAllMerch() {
        List<MerchDetailedResponseDTO> allMerch = merchRepository.findAll().stream()
                .map(merchMapper::toDetailedResponseDTO)
                .map(this::enrichDetailedResponse)
                .collect(Collectors.toList());

        String studentId = studentService.getCurrentStudentId();
        if (studentId != null) {
            boolean shouldExcludeMembership = studentMembershipRepository.hasActiveMembership(studentId)
                    || cartItemRepository.existsByStudentIdAndMerchType(studentId, MerchType.MEMBERSHIP)
                    || orderItemRepository.existsByStudentIdAndMerchTypeAndStatus(studentId, MerchType.MEMBERSHIP, OrderStatus.PENDING);

            if (shouldExcludeMembership) {
                allMerch.removeIf(m -> m.getMerchType() == MerchType.MEMBERSHIP);
            }
        }
        return allMerch;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchSummaryResponseDTO> getAllMerchSummaries() {
        List<MerchSummaryResponseDTO> summaries = merchRepository.findAllSummaries().stream()
            .map(this::enrichSummaryResponse)
            .toList();

        String studentId = studentService.getCurrentStudentId();
        if (studentId != null) {
            boolean shouldExcludeMembership = studentMembershipRepository.hasActiveMembership(studentId)
                    || cartItemRepository.existsByStudentIdAndMerchType(studentId, MerchType.MEMBERSHIP)
                    || orderItemRepository.existsByStudentIdAndMerchTypeAndStatus(studentId, MerchType.MEMBERSHIP, OrderStatus.PENDING);

            if (shouldExcludeMembership) {
                return summaries.stream()
                    .filter(m -> m.getMerchType() != MerchType.MEMBERSHIP)
                    .toList();
            }
        }
        return summaries;
    }

    @Override
    @Transactional(readOnly = true)
    public MerchDetailedResponseDTO getMerchById(Long id) {
        Merch merch = merchRepository.findById(id)
                .orElseThrow(() -> new MerchNotFoundException("Merch not found with id: " + id));
        return enrichDetailedResponse(merchMapper.toDetailedResponseDTO(merch));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchSummaryResponseDTO> getMerchByType(MerchType merchType) {
        if (merchType == null) {
            throw new InvalidRequestException("Merch type is required");
        }

        String studentId = studentService.getCurrentStudentId();
        if (studentId != null && merchType == MerchType.MEMBERSHIP) {
            boolean shouldExcludeMembership = studentMembershipRepository.hasActiveMembership(studentId)
                    || cartItemRepository.existsByStudentIdAndMerchType(studentId, MerchType.MEMBERSHIP)
                    || orderItemRepository.existsByStudentIdAndMerchTypeAndStatus(studentId, MerchType.MEMBERSHIP, OrderStatus.PENDING);
            if (shouldExcludeMembership) {
                return List.of();
            }
        }

        return merchRepository.findAllSummaryByType(merchType).stream()
            .map(this::enrichSummaryResponse)
            .toList();
    }

    @Override
    @Transactional
    public MerchDetailedResponseDTO putMerch(Long merchId, MerchUpdateRequestDTO request) throws IOException {
        Merch foundMerch = merchRepository.findById(merchId)
                .orElseThrow(() -> new MerchNotFoundException("Merch not found with id: " + merchId));

        if (request.getMerchName() == null || request.getMerchName().trim().isEmpty()
            || request.getDescription() == null || request.getDescription().trim().isEmpty()
            || request.getMerchType() == null) {
            throw new InvalidRequestException("Merch name, description, and type are required");
        }

        if (!foundMerch.getMerchName().equals(request.getMerchName())
            && merchRepository.existsByMerchName(request.getMerchName())) {
            throw new MerchAlreadyExistException("Merch name already exists");
        }

        foundMerch.setMerchName(request.getMerchName().trim());
        foundMerch.setDescription(request.getDescription().trim());
        foundMerch.setMerchType(request.getMerchType());
        foundMerch.setHasFreebie(Boolean.TRUE.equals(request.getHasFreebie()));

        Merch updated = merchRepository.save(foundMerch);
        ticketFreebieConfigService.syncConfigsForMerch(updated, request.getHasFreebie(), request.getFreebieConfigs());
        return enrichDetailedResponse(merchMapper.toDetailedResponseDTO(updated));
    }

    @Override
    @Transactional
    public MerchDetailedResponseDTO patchMerch(Long merchId, MerchUpdateRequestDTO request) throws IOException {
        Merch foundMerch = merchRepository.findById(merchId)
                .orElseThrow(() -> new MerchNotFoundException("Merch not found with id: " + merchId));

        if (request.getMerchName() != null && !request.getMerchName().trim().isEmpty()) {
            if (!foundMerch.getMerchName().equals(request.getMerchName())
                && merchRepository.existsByMerchName(request.getMerchName())) {
                throw new MerchAlreadyExistException("Merch name already exists");
            }
            foundMerch.setMerchName(request.getMerchName().trim());
        }
        if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
            foundMerch.setDescription(request.getDescription().trim());
        }
        if (request.getMerchType() != null) {
            foundMerch.setMerchType(request.getMerchType());
        }
        if (request.getHasFreebie() != null) {
            foundMerch.setHasFreebie(request.getHasFreebie());
        }

        Merch updated = merchRepository.save(foundMerch);
        ticketFreebieConfigService.syncConfigsForMerch(updated, updated.getHasFreebie(), request.getFreebieConfigs());
        return enrichDetailedResponse(merchMapper.toDetailedResponseDTO(updated));
    }

    @Override
    @Transactional
    public void deleteMerch(Long merchId) {
        Merch merch = merchRepository.findById(merchId)
                .orElseThrow(() -> new MerchNotFoundException("Merch not found with id: " + merchId));
        merch.setIsActive(false);
        merchRepository.save(merch);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MerchDetailedResponseDTO> getArchivedMerch(Pageable pageable) {
        Page<Merch> archivedMerch = merchRepository.findArchivedMerch(pageable);
        return archivedMerch.map(merchMapper::toDetailedResponseDTO).map(this::enrichDetailedResponse);
    }

    @Override
    @Transactional
    public MerchDetailedResponseDTO revertMerch(Long merchId) {
        Merch merch = merchRepository.findByIdAndIsInactive(merchId)
                .orElseThrow(() -> new MerchNotFoundException("Archived merch not found with id: " + merchId));

        if (merch.getIsActive()) {
            throw new InvalidRequestException("Merch with id " + merchId + " is already active");
        }

        merch.setIsActive(true);
        Merch reverted = merchRepository.save(merch);
        return enrichDetailedResponse(merchMapper.toDetailedResponseDTO(reverted));
    }

    private void validateAndPersistVariants(Long merchId, MerchType merchType, List<MerchVariantRequestDTO> variants) throws IOException {
        if (variants == null || variants.isEmpty()) {
            throw new InvalidRequestException("At least one variant is required");
        }

        for (MerchVariantRequestDTO variantDto : variants) {
            validateVariantRequest(merchType, variantDto);
        }

        for (MerchVariantRequestDTO variantDto : variants) {
            merchVariantService.addVariantToMerch(merchId, variantDto);
        }
    }

    private void validateVariantRequest(MerchType merchType, MerchVariantRequestDTO variantDto) {
        if (variantDto == null) {
            throw new InvalidRequestException("Variant request is required");
        }

        if (merchType == MerchType.CLOTHING) {
            if (variantDto.getColor() == null || variantDto.getColor().trim().isEmpty()) {
                throw new InvalidRequestException("Color is required for clothing variants");
            }
            if (variantDto.getDesign() != null && !variantDto.getDesign().trim().isEmpty()) {
                throw new InvalidRequestException("Design is not allowed for clothing variants");
            }
        } else {
            if (variantDto.getDesign() == null || variantDto.getDesign().trim().isEmpty()) {
                throw new InvalidRequestException("Design is required for non-clothing variants");
            }
            if (variantDto.getColor() != null && !variantDto.getColor().trim().isEmpty()) {
                throw new InvalidRequestException("Color is not allowed for non-clothing variants");
            }
        }

        List<MerchVariantItemRequestDTO> items = variantDto.getVariantItems();
        if (items == null || items.isEmpty()) {
            throw new InvalidRequestException("At least one item (size/price/stock) is required for each variant");
        }
    }

    private MerchDetailedResponseDTO enrichDetailedResponse(MerchDetailedResponseDTO response) {
        response.setFreebieConfigs(ticketFreebieConfigService.getConfigsByTicketMerchId(response.getMerchId()));
        enrichPurchaseState(response.getMerchId(), response.getMerchType(), response::setPurchaseBlocked, response::setPurchaseBlockMessage);
        return response;
    }

    private MerchSummaryResponseDTO enrichSummaryResponse(MerchSummaryResponseDTO response) {
        response.setFreebieConfigs(ticketFreebieConfigService.getConfigsByTicketMerchId(response.getMerchId()));
        enrichPurchaseState(response.getMerchId(), response.getMerchType(), response::setPurchaseBlocked, response::setPurchaseBlockMessage);
        return response;
    }

    private void enrichPurchaseState(
            Long merchId,
            MerchType merchType,
            java.util.function.Consumer<Boolean> purchaseBlockedConsumer,
            java.util.function.Consumer<String> purchaseBlockMessageConsumer) {
        String studentId = studentService.getCurrentStudentId();
        if (studentId == null || merchType == null) {
            purchaseBlockedConsumer.accept(Boolean.FALSE);
            purchaseBlockMessageConsumer.accept(null);
            return;
        }

        boolean purchaseBlocked = false;
        String purchaseBlockMessage = null;

        if (merchType == MerchType.TICKET) {
            purchaseBlocked = cartItemRepository.existsByStudentIdAndMerchId(studentId, merchId)
                    || orderItemRepository.existsByStudentIdAndMerchIdAndOrderStatusNotIn(
                            studentId,
                            merchId,
                            List.of(OrderStatus.CANCELLED, OrderStatus.REJECTED));
            purchaseBlockMessage = purchaseBlocked ? ITEM_ALREADY_IN_CART_OR_ORDER : null;
        } else if (merchType == MerchType.MEMBERSHIP) {
            boolean hasMembershipConflict = studentMembershipRepository.hasActiveMembership(studentId)
                    || cartItemRepository.existsByStudentIdAndMerchType(studentId, MerchType.MEMBERSHIP)
                    || orderItemRepository.existsByStudentIdAndMerchTypeAndStatus(studentId, MerchType.MEMBERSHIP, OrderStatus.PENDING);
            purchaseBlocked = hasMembershipConflict;
            purchaseBlockMessage = hasMembershipConflict ? ITEM_ALREADY_IN_CART_OR_ORDER : null;
        }

        purchaseBlockedConsumer.accept(purchaseBlocked);
        purchaseBlockMessageConsumer.accept(purchaseBlockMessage);
    }
}
