package org.csps.backend.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.csps.backend.domain.dtos.request.TicketFreebieConfigRequestDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieConfigResponseDTO;
import org.csps.backend.domain.entities.Merch;
import org.csps.backend.domain.entities.MerchVariantItem;
import org.csps.backend.domain.entities.TicketFreebieColorOption;
import org.csps.backend.domain.entities.TicketFreebieConfig;
import org.csps.backend.domain.entities.TicketFreebieDesignOption;
import org.csps.backend.domain.entities.TicketFreebieSizeOption;
import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.TicketFreebieCategory;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.MerchVariantNotFoundException;
import org.csps.backend.mapper.TicketFreebieConfigMapper;
import org.csps.backend.repository.CartItemFreebieSelectionRepository;
import org.csps.backend.repository.MerchVariantItemRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.TicketFreebieAssignmentRepository;
import org.csps.backend.repository.TicketFreebieConfigRepository;
import org.csps.backend.service.TicketFreebieConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketFreebieConfigServiceImpl implements TicketFreebieConfigService {

    private final TicketFreebieConfigRepository ticketFreebieConfigRepository;
    private final TicketFreebieAssignmentRepository ticketFreebieAssignmentRepository;
    private final TicketFreebieConfigMapper ticketFreebieConfigMapper;
    private final OrderItemRepository orderItemRepository;
    private final MerchVariantItemRepository merchVariantItemRepository;
    private final CartItemFreebieSelectionRepository cartItemFreebieSelectionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TicketFreebieConfigResponseDTO> getConfigsByTicketMerchId(Long ticketMerchId) {
        return ticketFreebieConfigRepository.findByTicketMerchMerchIdOrderByDisplayOrderAscTicketFreebieConfigIdAsc(ticketMerchId).stream()
                .map(ticketFreebieConfigMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketFreebieConfigResponseDTO> getConfigsByMerchVariantItemId(Long merchVariantItemId) {
        MerchVariantItem merchVariantItem = merchVariantItemRepository.findById(merchVariantItemId)
                .orElseThrow(() -> new MerchVariantNotFoundException("Merch variant item not found"));
        return getConfigsByTicketMerchId(merchVariantItem.getMerchVariant().getMerch().getMerchId());
    }

    @Override
    @Transactional
    public void syncConfigsForMerch(Merch merch, Boolean hasFreebie, List<TicketFreebieConfigRequestDTO> requests) {
        boolean shouldHaveFreebies = Boolean.TRUE.equals(hasFreebie);
        List<TicketFreebieConfig> existingConfigs =
                new ArrayList<>(ticketFreebieConfigRepository.findByTicketMerchMerchIdOrderByDisplayOrderAscTicketFreebieConfigIdAsc(merch.getMerchId()));
        List<TicketFreebieConfigRequestDTO> normalizedRequests = requests == null ? List.of() : requests;

        if (merch.getMerchType() != MerchType.TICKET) {
            if (shouldHaveFreebies || !normalizedRequests.isEmpty() || !existingConfigs.isEmpty()) {
                throw new InvalidRequestException("Freebie configuration is only allowed for ticket merchandise");
            }
            merch.setHasFreebie(false);
            return;
        }

        boolean hasDependentCartSelections =
                cartItemFreebieSelectionRepository.existsByTicketFreebieConfigTicketMerchMerchId(merch.getMerchId());

        if (!shouldHaveFreebies) {
            if (!normalizedRequests.isEmpty()) {
                throw new InvalidRequestException("Freebie configs must not be submitted when hasFreebie is false");
            }
            if (!existingConfigs.isEmpty()) {
                if (orderItemRepository.existsByMerch(merch.getMerchId()) || hasDependentCartSelections) {
                    throw new InvalidRequestException("Cannot disable freebies for a ticket that already has buyers or active cart selections");
                }
                ticketFreebieConfigRepository.deleteAll(existingConfigs);
            }
            merch.setHasFreebie(false);
            return;
        }

        if (normalizedRequests.isEmpty()) {
            if (existingConfigs.isEmpty()) {
                throw new InvalidRequestException("At least one freebie config is required when hasFreebie is true");
            }
            merch.setHasFreebie(true);
            return;
        }

        merch.setHasFreebie(true);
        boolean hasDependentOrders = orderItemRepository.existsByMerch(merch.getMerchId());
        boolean hasDependentSelections = hasDependentOrders || hasDependentCartSelections;
        Map<Long, TicketFreebieConfig> existingById = existingConfigs.stream()
                .filter(config -> config.getTicketFreebieConfigId() != null)
                .collect(LinkedHashMap::new, (map, config) -> map.put(config.getTicketFreebieConfigId(), config), Map::putAll);
        Set<Long> retainedIds = new LinkedHashSet<>();
        List<TicketFreebieConfig> configsToSave = new ArrayList<>();

        int index = 0;
        for (TicketFreebieConfigRequestDTO request : normalizedRequests) {
            ValidatedFreebieConfig validated = validateAndNormalize(request, index);
            TicketFreebieConfig config = null;
            if (validated.configId() != null) {
                config = existingById.get(validated.configId());
                if (config == null) {
                    throw new InvalidRequestException("Freebie config does not belong to this merch: " + validated.configId());
                }
            }

            if (config == null) {
                config = TicketFreebieConfig.builder()
                        .ticketMerch(merch)
                        .build();
            } else if (hasDependentSelections) {
                validateCategoryAndOptionSafety(config, validated);
                retainedIds.add(config.getTicketFreebieConfigId());
            }

            config.setTicketMerch(merch);
            config.setDisplayOrder(validated.displayOrder());
            config.setCategory(validated.category());
            config.setFreebieName(validated.freebieName());
            config.setClothingSubtype(validated.clothingSubtype());
            replaceOptions(config, validated);
            configsToSave.add(config);
            index++;
        }

        if (hasDependentSelections) {
            for (TicketFreebieConfig existingConfig : existingConfigs) {
                if (existingConfig.getTicketFreebieConfigId() != null && !retainedIds.contains(existingConfig.getTicketFreebieConfigId())) {
                    throw new InvalidRequestException("Cannot remove a freebie config after buyers or active cart selections already exist for this ticket");
                }
            }
        } else {
            // Flush deletions before inserts so replacement writes do not race old rows inside
            // the same transaction when the database still enforces older uniqueness assumptions.
            ticketFreebieConfigRepository.deleteAll(existingConfigs);
            ticketFreebieConfigRepository.flush();
        }

        ticketFreebieConfigRepository.saveAll(configsToSave);
    }

    private ValidatedFreebieConfig validateAndNormalize(TicketFreebieConfigRequestDTO request, int fallbackOrder) {
        if (request == null) {
            throw new InvalidRequestException("Freebie config entries must not be null");
        }

        String freebieName = normalizeRequiredText(request.getFreebieName(), "Freebie name is required");
        String clothingSubtype = normalizeOptionalText(request.getClothingSubtype());
        TicketFreebieCategory category = request.getCategory();
        if (category == null) {
            throw new InvalidRequestException("Freebie category is required");
        }

        int displayOrder = request.getDisplayOrder() == null ? fallbackOrder : request.getDisplayOrder();
        if (displayOrder < 0) {
            throw new InvalidRequestException("Freebie display order must be non-negative");
        }

        List<ClothingSizing> sizes = List.of();
        List<String> colors = List.of();
        List<String> designs = List.of();

        if (category == TicketFreebieCategory.CLOTHING) {
            sizes = normalizeSizes(request.getSizes());
            colors = normalizeTextList(request.getColors(), "At least one color is required");
            if (request.getDesigns() != null && !request.getDesigns().isEmpty()) {
                throw new InvalidRequestException("Designs are not allowed for clothing freebies");
            }
        } else {
            designs = normalizeTextList(request.getDesigns(), "At least one design or variant is required");
            if (request.getSizes() != null && !request.getSizes().isEmpty()) {
                throw new InvalidRequestException("Sizes are not allowed for non-clothing freebies");
            }
            if (request.getColors() != null && !request.getColors().isEmpty()) {
                throw new InvalidRequestException("Colors are not allowed for non-clothing freebies");
            }
            clothingSubtype = null;
        }

        return new ValidatedFreebieConfig(request.getTicketFreebieConfigId(), displayOrder, category, freebieName, clothingSubtype, sizes, colors, designs);
    }

    private void validateCategoryAndOptionSafety(TicketFreebieConfig existingConfig, ValidatedFreebieConfig validated) {
        if (existingConfig.getCategory() != validated.category()) {
            throw new InvalidRequestException("Cannot change freebie category after buyers or active cart selections already exist for this ticket");
        }

        Set<ClothingSizing> newSizes = new LinkedHashSet<>(validated.sizes());
        for (TicketFreebieSizeOption option : existingConfig.getSizeOptions()) {
            if (!newSizes.contains(option.getSizeLabel()) && isSizeInUse(existingConfig.getTicketFreebieConfigId(), option.getSizeLabel())) {
                throw new InvalidRequestException("Cannot remove size " + option.getSizeLabel() + " because it is already in use");
            }
        }

        Set<String> newColors = lowercaseSet(validated.colors());
        for (TicketFreebieColorOption option : existingConfig.getColorOptions()) {
            if (!newColors.contains(option.getColorLabel().toLowerCase(Locale.ROOT))
                    && isColorInUse(existingConfig.getTicketFreebieConfigId(), option.getColorLabel())) {
                throw new InvalidRequestException("Cannot remove color " + option.getColorLabel() + " because it is already in use");
            }
        }

        Set<String> newDesigns = lowercaseSet(validated.designs());
        for (TicketFreebieDesignOption option : existingConfig.getDesignOptions()) {
            if (!newDesigns.contains(option.getDesignLabel().toLowerCase(Locale.ROOT))
                    && isDesignInUse(existingConfig.getTicketFreebieConfigId(), option.getDesignLabel())) {
                throw new InvalidRequestException("Cannot remove design " + option.getDesignLabel() + " because it is already in use");
            }
        }
    }

    private boolean isSizeInUse(Long configId, ClothingSizing size) {
        return ticketFreebieAssignmentRepository.countByConfigIdAndSelectedSize(configId, size) > 0
                || cartItemFreebieSelectionRepository.countByTicketFreebieConfigTicketFreebieConfigIdAndSelectedSize(configId, size) > 0;
    }

    private boolean isColorInUse(Long configId, String color) {
        return ticketFreebieAssignmentRepository.countByConfigIdAndSelectedColor(configId, color) > 0
                || cartItemFreebieSelectionRepository.countByTicketFreebieConfigTicketFreebieConfigIdAndSelectedColorIgnoreCase(configId, color) > 0;
    }

    private boolean isDesignInUse(Long configId, String design) {
        return ticketFreebieAssignmentRepository.countByConfigIdAndSelectedDesign(configId, design) > 0
                || cartItemFreebieSelectionRepository.countByTicketFreebieConfigTicketFreebieConfigIdAndSelectedDesignIgnoreCase(configId, design) > 0;
    }

    private void replaceOptions(TicketFreebieConfig config, ValidatedFreebieConfig validated) {
        syncSizeOptions(config, validated.sizes());
        syncColorOptions(config, validated.colors());
        syncDesignOptions(config, validated.designs());
    }

    private void syncSizeOptions(TicketFreebieConfig config, List<ClothingSizing> desiredSizes) {
        Set<ClothingSizing> desired = new LinkedHashSet<>(desiredSizes);
        config.getSizeOptions().removeIf(option -> !desired.contains(option.getSizeLabel()));

        Set<ClothingSizing> existing = config.getSizeOptions().stream()
                .map(TicketFreebieSizeOption::getSizeLabel)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        for (ClothingSizing size : desiredSizes) {
            if (!existing.contains(size)) {
                config.getSizeOptions().add(TicketFreebieSizeOption.builder()
                        .ticketFreebieConfig(config)
                        .sizeLabel(size)
                        .build());
            }
        }
    }

    private void syncColorOptions(TicketFreebieConfig config, List<String> desiredColors) {
        Set<String> desiredLower = lowercaseSet(desiredColors);
        config.getColorOptions().removeIf(option -> !desiredLower.contains(option.getColorLabel().toLowerCase(Locale.ROOT)));

        Set<String> existingLower = config.getColorOptions().stream()
                .map(option -> option.getColorLabel().toLowerCase(Locale.ROOT))
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        for (String color : desiredColors) {
            String lowered = color.toLowerCase(Locale.ROOT);
            if (!existingLower.contains(lowered)) {
                config.getColorOptions().add(TicketFreebieColorOption.builder()
                        .ticketFreebieConfig(config)
                        .colorLabel(color)
                        .build());
            }
        }
    }

    private void syncDesignOptions(TicketFreebieConfig config, List<String> desiredDesigns) {
        Set<String> desiredLower = lowercaseSet(desiredDesigns);
        config.getDesignOptions().removeIf(option -> !desiredLower.contains(option.getDesignLabel().toLowerCase(Locale.ROOT)));

        Set<String> existingLower = config.getDesignOptions().stream()
                .map(option -> option.getDesignLabel().toLowerCase(Locale.ROOT))
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        for (String design : desiredDesigns) {
            String lowered = design.toLowerCase(Locale.ROOT);
            if (!existingLower.contains(lowered)) {
                config.getDesignOptions().add(TicketFreebieDesignOption.builder()
                        .ticketFreebieConfig(config)
                        .designLabel(design)
                        .build());
            }
        }
    }

    private List<ClothingSizing> normalizeSizes(List<ClothingSizing> rawSizes) {
        if (rawSizes == null || rawSizes.isEmpty()) {
            throw new InvalidRequestException("At least one size is required");
        }
        Set<ClothingSizing> seen = new LinkedHashSet<>();
        for (ClothingSizing size : rawSizes) {
            if (size == null) {
                throw new InvalidRequestException("Size values must not be empty");
            }
            if (!seen.add(size)) {
                throw new InvalidRequestException("Duplicate size values are not allowed: " + size);
            }
        }
        return new ArrayList<>(seen);
    }

    private List<String> normalizeTextList(List<String> rawValues, String emptyMessage) {
        if (rawValues == null || rawValues.isEmpty()) {
            throw new InvalidRequestException(emptyMessage);
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> normalizedValues = new ArrayList<>();
        for (String rawValue : rawValues) {
            String normalized = normalizeRequiredText(rawValue, "Freebie option values must not be empty");
            if (!seen.add(normalized.toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("Duplicate freebie option values are not allowed: " + normalized);
            }
            normalizedValues.add(normalized);
        }
        return normalizedValues;
    }

    private Set<String> lowercaseSet(List<String> values) {
        Set<String> lowered = new LinkedHashSet<>();
        for (String value : values) {
            lowered.add(value.toLowerCase(Locale.ROOT));
        }
        return lowered;
    }

    private String normalizeRequiredText(String rawValue, String message) {
        String normalized = normalizeOptionalText(rawValue);
        if (normalized == null) {
            throw new InvalidRequestException(message);
        }
        return normalized;
    }

    private String normalizeOptionalText(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private record ValidatedFreebieConfig(
            Long configId,
            Integer displayOrder,
            TicketFreebieCategory category,
            String freebieName,
            String clothingSubtype,
            List<ClothingSizing> sizes,
            List<String> colors,
            List<String> designs) {
    }
}
