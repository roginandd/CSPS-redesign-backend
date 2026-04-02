package org.csps.backend.controller;

import java.io.IOException;
import java.util.List;

import org.csps.backend.annotation.Auditable;
import org.csps.backend.domain.dtos.request.MerchRequestDTO;
import org.csps.backend.domain.dtos.request.MerchUpdateRequestDTO;
import org.csps.backend.domain.dtos.request.MerchVariantRequestDTO;
import org.csps.backend.domain.dtos.request.TicketFreebieConfigRequestDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.dtos.response.MerchDetailedResponseDTO;
import org.csps.backend.domain.dtos.response.MerchSummaryResponseDTO;
import org.csps.backend.domain.enums.AuditAction;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.service.MerchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/merch")
@RequiredArgsConstructor
public class MerchController {

    private final MerchService merchService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a merch item and, for tickets, optionally stores zero or many inline freebie configs.
     */
    @PostMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "Merch")
    public ResponseEntity<GlobalResponseBuilder<MerchDetailedResponseDTO>> createMerch(
            @RequestParam String merchName,
            @RequestParam String description,
            @RequestParam MerchType merchType,
            @RequestParam(required = true) Double basePrice,
            @RequestParam(required = false) String s3ImageKey,
            @RequestParam(required = true) MultipartFile merchImage,
            @RequestParam String variants,
            @RequestParam(required = false) MultipartFile[] variantImages,
            @RequestParam(required = false, defaultValue = "false") Boolean hasFreebie,
            @RequestParam(required = false) String freebieConfigs
    ) throws IOException {
        List<MerchVariantRequestDTO> variantsList = objectMapper.readValue(
            variants,
            new TypeReference<List<MerchVariantRequestDTO>>() {}
        );

        if (variantImages != null) {
            for (int i = 0; i < variantsList.size() && i < variantImages.length; i++) {
                variantsList.get(i).setVariantImage(variantImages[i]);
            }
        }

        List<TicketFreebieConfigRequestDTO> parsedFreebieConfigs = parseFreebieConfigs(freebieConfigs);

        MerchRequestDTO request = MerchRequestDTO.builder()
                .merchName(merchName)
                .description(description)
                .merchType(merchType)
                .basePrice(basePrice)
                .s3ImageKey(s3ImageKey)
                .merchImage(merchImage)
                .hasFreebie(hasFreebie)
                .freebieConfigs(parsedFreebieConfigs)
                .merchVariantRequestDto(variantsList)
                .build();

        MerchDetailedResponseDTO createdMerch = merchService.createMerch(request);
        return GlobalResponseBuilder.buildResponse("Merchandise created successfully", createdMerch, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<List<MerchDetailedResponseDTO>> getAllMerch() {
        return ResponseEntity.ok(merchService.getAllMerch());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<List<MerchSummaryResponseDTO>> getAllMerchSummaries() {
        return ResponseEntity.ok(merchService.getAllMerchSummaries());
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<List<MerchSummaryResponseDTO>> getMerchByType(@PathVariable MerchType type) {
        return ResponseEntity.ok(merchService.getMerchByType(type));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<MerchDetailedResponseDTO> getMerchById(@PathVariable Long id) {
        return ResponseEntity.ok(merchService.getMerchById(id));
    }

    @PutMapping("/{merchId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "Merch")
    public ResponseEntity<GlobalResponseBuilder<MerchDetailedResponseDTO>> putMerch(
            @PathVariable Long merchId,
            @Valid @RequestBody MerchUpdateRequestDTO request
    ) throws IOException {
        MerchDetailedResponseDTO response = merchService.putMerch(merchId, request);
        return GlobalResponseBuilder.buildResponse("Merch updated successfully", response, HttpStatus.OK);
    }

    @PatchMapping("/{merchId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "Merch")
    public ResponseEntity<GlobalResponseBuilder<MerchDetailedResponseDTO>> patchMerch(
            @PathVariable Long merchId,
            @RequestBody MerchUpdateRequestDTO request
    ) throws IOException {
        MerchDetailedResponseDTO response = merchService.patchMerch(merchId, request);
        return GlobalResponseBuilder.buildResponse("Merch updated successfully", response, HttpStatus.OK);
    }

    @DeleteMapping("/{merchId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<Void>> deleteMerch(@PathVariable Long merchId) {
        merchService.deleteMerch(merchId);
        return GlobalResponseBuilder.buildResponse("Merch archived successfully", null, HttpStatus.NO_CONTENT);
    }

    @GetMapping("/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponseBuilder<Page<MerchDetailedResponseDTO>>> getArchivedMerch(
            @PageableDefault(size = 10, page = 0) Pageable pageable
    ) {
        Page<MerchDetailedResponseDTO> archived = merchService.getArchivedMerch(pageable);
        return GlobalResponseBuilder.buildResponse("Retrieved archived merchandise", archived, HttpStatus.OK);
    }

    @PutMapping("/{merchId}/revert")
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE') or hasRole('ADMIN_FINANCE')")
    public ResponseEntity<GlobalResponseBuilder<MerchDetailedResponseDTO>> revertMerch(@PathVariable Long merchId) {
        MerchDetailedResponseDTO reverted = merchService.revertMerch(merchId);
        return GlobalResponseBuilder.buildResponse("Merchandise reverted to active status", reverted, HttpStatus.OK);
    }

    private List<TicketFreebieConfigRequestDTO> parseFreebieConfigs(String freebieConfigs) throws IOException {
        if (freebieConfigs == null || freebieConfigs.trim().isEmpty()) {
            return List.of();
        }
        return objectMapper.readValue(freebieConfigs, new TypeReference<List<TicketFreebieConfigRequestDTO>>() {});
    }
}
