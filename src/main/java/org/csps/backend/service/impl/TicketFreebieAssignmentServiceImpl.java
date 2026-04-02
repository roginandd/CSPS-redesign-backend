package org.csps.backend.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.csps.backend.domain.dtos.request.TicketFreebieAssignmentRequestDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieAssignmentResponseDTO;
import org.csps.backend.domain.entities.OrderItem;
import org.csps.backend.domain.entities.TicketFreebieAssignment;
import org.csps.backend.domain.entities.TicketFreebieConfig;
import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.TicketFreebieCategory;
import org.csps.backend.domain.enums.TicketFreebieFulfillmentStatus;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.OrderItemNotFoundException;
import org.csps.backend.mapper.TicketFreebieAssignmentMapper;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.TicketFreebieAssignmentRepository;
import org.csps.backend.repository.TicketFreebieConfigRepository;
import org.csps.backend.service.TicketFreebieAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketFreebieAssignmentServiceImpl implements TicketFreebieAssignmentService {

    private final OrderItemRepository orderItemRepository;
    private final TicketFreebieAssignmentRepository ticketFreebieAssignmentRepository;
    private final TicketFreebieConfigRepository ticketFreebieConfigRepository;
    private final TicketFreebieAssignmentMapper ticketFreebieAssignmentMapper;

    @Override
    @Transactional
    public List<TicketFreebieAssignmentResponseDTO> initializeAssignments(Long orderItemId, List<TicketFreebieAssignmentRequestDTO> requests) {
        OrderItem orderItem = getOrderItem(orderItemId);
        TicketContext ticketContext = resolveTicketContext(orderItem);
        if (!ticketContext.hasFreebie()) {
            if (requests != null && !requests.isEmpty()) {
                throw new InvalidRequestException("This order item does not support freebies");
            }
            return List.of(buildNoFreebieResponse(orderItemId));
        }

        return upsertAssignmentsInternal(orderItem, ticketContext, requests, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketFreebieAssignmentResponseDTO> getAssignmentsByOrderItemId(Long orderItemId) {
        OrderItem orderItem = getOrderItem(orderItemId);
        TicketContext ticketContext = resolveTicketContext(orderItem);
        return buildResponses(
                orderItemId,
                ticketContext,
                ticketFreebieAssignmentRepository.findByOrderItemOrderItemId(orderItemId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<TicketFreebieAssignmentResponseDTO>> getAssignmentsByOrderItemIds(List<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) {
            return Map.of();
        }

        List<Long> normalizedOrderItemIds = orderItemIds.stream()
                .filter(orderItemId -> orderItemId != null && orderItemId > 0)
                .distinct()
                .toList();
        if (normalizedOrderItemIds.isEmpty()) {
            return Map.of();
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderItemIdIn(normalizedOrderItemIds);
        Map<Long, OrderItem> orderItemsById = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getOrderItemId, orderItem -> orderItem));
        if (orderItemsById.size() != normalizedOrderItemIds.size()) {
            throw new OrderItemNotFoundException("Order item not found");
        }

        List<TicketFreebieAssignment> assignments = ticketFreebieAssignmentRepository.findByOrderItemOrderItemIdIn(normalizedOrderItemIds);
        Map<Long, List<TicketFreebieAssignment>> assignmentMap = assignments.stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getOrderItem().getOrderItemId()));

        Map<Long, List<TicketFreebieConfig>> configsByMerchId = loadConfigsByMerchId(orderItems);
        Map<Long, List<TicketFreebieAssignmentResponseDTO>> responseMap = new LinkedHashMap<>();
        for (Long orderItemId : normalizedOrderItemIds) {
            OrderItem orderItem = orderItemsById.get(orderItemId);
            TicketContext ticketContext = resolveTicketContext(orderItem, configsByMerchId);
            responseMap.put(
                    orderItemId,
                    buildResponses(orderItemId, ticketContext, assignmentMap.getOrDefault(orderItemId, List.of())));
        }
        return responseMap;
    }

    @Override
    @Transactional
    public List<TicketFreebieAssignmentResponseDTO> upsertAssignments(Long orderItemId, List<TicketFreebieAssignmentRequestDTO> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new InvalidRequestException("Freebie assignment request is required");
        }

        OrderItem orderItem = getOrderItem(orderItemId);
        TicketContext ticketContext = resolveTicketContext(orderItem);
        if (!ticketContext.hasFreebie()) {
            throw new InvalidRequestException("This order item does not support freebies");
        }

        return upsertAssignmentsInternal(orderItem, ticketContext, requests, false);
    }

    private List<TicketFreebieAssignmentResponseDTO> upsertAssignmentsInternal(
            OrderItem orderItem,
            TicketContext ticketContext,
            List<TicketFreebieAssignmentRequestDTO> requests,
            boolean allowEmptyForInitialize) {
        List<TicketFreebieAssignmentRequestDTO> normalizedRequests = requests == null ? List.of() : requests;
        Map<Long, TicketFreebieAssignmentRequestDTO> requestByConfigId = new LinkedHashMap<>();
        for (TicketFreebieAssignmentRequestDTO request : normalizedRequests) {
            if (request == null || request.getTicketFreebieConfigId() == null) {
                throw new InvalidRequestException("Each freebie assignment must include ticketFreebieConfigId");
            }
            if (requestByConfigId.put(request.getTicketFreebieConfigId(), request) != null) {
                throw new InvalidRequestException("Duplicate freebie assignments are not allowed for the same config");
            }
        }

        Map<Long, TicketFreebieAssignment> existingByConfigId = ticketFreebieAssignmentRepository.findByOrderItemOrderItemId(orderItem.getOrderItemId()).stream()
                .filter(assignment -> assignment.getTicketFreebieConfig() != null && assignment.getTicketFreebieConfig().getTicketFreebieConfigId() != null)
                .collect(Collectors.toMap(assignment -> assignment.getTicketFreebieConfig().getTicketFreebieConfigId(), assignment -> assignment));

        List<TicketFreebieAssignment> assignmentsToSave = new ArrayList<>();
        for (TicketFreebieConfig config : ticketContext.configs()) {
            TicketFreebieAssignmentRequestDTO request = requestByConfigId.remove(config.getTicketFreebieConfigId());
            if (request == null) {
                if (!allowEmptyForInitialize) {
                    throw new InvalidRequestException("Missing freebie assignment for config " + config.getFreebieName());
                }
                request = TicketFreebieAssignmentRequestDTO.builder().ticketFreebieConfigId(config.getTicketFreebieConfigId()).build();
            }

            TicketFreebieAssignment assignment = existingByConfigId.getOrDefault(
                    config.getTicketFreebieConfigId(),
                    TicketFreebieAssignment.builder().orderItem(orderItem).ticketFreebieConfig(config).build());
            assignment.setTicketFreebieConfig(config);
            applyRequest(config, assignment, request);
            assignmentsToSave.add(assignment);
        }

        if (!requestByConfigId.isEmpty()) {
            throw new InvalidRequestException("Submitted freebie assignments contain unknown configs");
        }

        List<TicketFreebieAssignment> savedAssignments = ticketFreebieAssignmentRepository.saveAll(assignmentsToSave);
        return ticketContext.configs().stream()
                .map(config -> savedAssignments.stream()
                        .filter(assignment -> config.getTicketFreebieConfigId().equals(assignment.getTicketFreebieConfig().getTicketFreebieConfigId()))
                        .findFirst()
                        .map(assignment -> buildResponse(config, assignment))
                        .orElseGet(() -> buildPendingResponse(config, orderItem.getOrderItemId())))
                .toList();
    }

    private void applyRequest(
            TicketFreebieConfig config,
            TicketFreebieAssignment assignment,
            TicketFreebieAssignmentRequestDTO request) {
        if (config.getCategory() == TicketFreebieCategory.CLOTHING) {
            assignment.setSelectedSize(request.getSelectedSize());
            assignment.setSelectedColor(normalizeOptionalText(request.getSelectedColor()));
            assignment.setSelectedDesign(null);
            validateClothingSelection(config, assignment);
        } else {
            assignment.setSelectedSize(null);
            assignment.setSelectedColor(null);
            assignment.setSelectedDesign(normalizeOptionalText(request.getSelectedDesign()));
            validateNonClothingSelection(config, assignment);
        }

        boolean detailsComplete = hasCompleteDetails(config, assignment);
        TicketFreebieFulfillmentStatus requestedStatus = request.getFulfillmentStatus();
        TicketFreebieFulfillmentStatus resolvedStatus = resolveStatus(assignment, requestedStatus, detailsComplete);
        assignment.setFulfillmentStatus(resolvedStatus);
        syncTimestamps(assignment, resolvedStatus);
    }

    private TicketFreebieFulfillmentStatus resolveStatus(
            TicketFreebieAssignment assignment,
            TicketFreebieFulfillmentStatus requestedStatus,
            boolean detailsComplete) {
        if (requestedStatus != null) {
            validateStatus(detailsComplete, requestedStatus);
            return requestedStatus;
        }

        if (!detailsComplete) {
            return TicketFreebieFulfillmentStatus.PENDING_DETAILS;
        }

        if (assignment.getFulfillmentStatus() == TicketFreebieFulfillmentStatus.CLAIMED
                || assignment.getFulfillmentStatus() == TicketFreebieFulfillmentStatus.FULFILLED) {
            return assignment.getFulfillmentStatus();
        }

        return TicketFreebieFulfillmentStatus.DETAILS_COMPLETED;
    }

    private void validateStatus(boolean detailsComplete, TicketFreebieFulfillmentStatus status) {
        if (status == TicketFreebieFulfillmentStatus.NO_FREEBIE) {
            throw new InvalidRequestException("NO_FREEBIE is only allowed for tickets without freebies");
        }

        if ((status == TicketFreebieFulfillmentStatus.DETAILS_COMPLETED
                || status == TicketFreebieFulfillmentStatus.CLAIMED
                || status == TicketFreebieFulfillmentStatus.FULFILLED) && !detailsComplete) {
            throw new InvalidRequestException("Freebie details must be complete before using status " + status);
        }
    }

    private void syncTimestamps(TicketFreebieAssignment assignment, TicketFreebieFulfillmentStatus status) {
        LocalDateTime now = LocalDateTime.now();
        if (status == TicketFreebieFulfillmentStatus.CLAIMED || status == TicketFreebieFulfillmentStatus.FULFILLED) {
            if (assignment.getClaimedAt() == null) {
                assignment.setClaimedAt(now);
            }
        } else {
            assignment.setClaimedAt(null);
        }

        if (status == TicketFreebieFulfillmentStatus.FULFILLED) {
            if (assignment.getFulfilledAt() == null) {
                assignment.setFulfilledAt(now);
            }
        } else {
            assignment.setFulfilledAt(null);
        }
    }

    private void validateClothingSelection(TicketFreebieConfig config, TicketFreebieAssignment assignment) {
        Set<ClothingSizing> allowedSizes = config.getSizeOptions().stream().map(option -> option.getSizeLabel()).collect(Collectors.toSet());
        Set<String> allowedColors = config.getColorOptions().stream()
                .map(option -> option.getColorLabel().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (assignment.getSelectedSize() != null && !allowedSizes.contains(assignment.getSelectedSize())) {
            throw new InvalidRequestException("Selected size is not allowed for " + config.getFreebieName());
        }
        if (assignment.getSelectedColor() != null
                && !allowedColors.contains(assignment.getSelectedColor().toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("Selected color is not allowed for " + config.getFreebieName());
        }
    }

    private void validateNonClothingSelection(TicketFreebieConfig config, TicketFreebieAssignment assignment) {
        Set<String> allowedDesigns = config.getDesignOptions().stream()
                .map(option -> option.getDesignLabel().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (assignment.getSelectedDesign() != null
                && !allowedDesigns.contains(assignment.getSelectedDesign().toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("Selected design is not allowed for " + config.getFreebieName());
        }
    }

    private boolean hasCompleteDetails(TicketFreebieConfig config, TicketFreebieAssignment assignment) {
        if (config.getCategory() == TicketFreebieCategory.CLOTHING) {
            return assignment.getSelectedSize() != null && assignment.getSelectedColor() != null;
        }
        return assignment.getSelectedDesign() != null;
    }

    private TicketContext resolveTicketContext(OrderItem orderItem) {
        return resolveTicketContext(orderItem, null);
    }

    private TicketContext resolveTicketContext(
            OrderItem orderItem,
            Map<Long, List<TicketFreebieConfig>> configsByMerchId) {
        if (orderItem.getMerchVariantItem() == null
                || orderItem.getMerchVariantItem().getMerchVariant() == null
                || orderItem.getMerchVariantItem().getMerchVariant().getMerch() == null) {
            throw new InvalidRequestException("Order item merch details are incomplete");
        }

        MerchType merchType = orderItem.getMerchVariantItem().getMerchVariant().getMerch().getMerchType();
        if (merchType != MerchType.TICKET) {
            return new TicketContext(false, List.of());
        }

        boolean hasFreebie = Boolean.TRUE.equals(orderItem.getMerchVariantItem().getMerchVariant().getMerch().getHasFreebie());
        if (!hasFreebie) {
            return new TicketContext(false, List.of());
        }

        Long ticketMerchId = orderItem.getMerchVariantItem().getMerchVariant().getMerch().getMerchId();
        List<TicketFreebieConfig> configs = configsByMerchId == null
                ? ticketFreebieConfigRepository.findByTicketMerchMerchIdOrderByDisplayOrderAscTicketFreebieConfigIdAsc(ticketMerchId)
                : configsByMerchId.getOrDefault(ticketMerchId, List.of());
        if (configs.isEmpty()) {
            throw new InvalidRequestException("Ticket freebie config is missing for merch id: " + ticketMerchId);
        }
        return new TicketContext(true, configs);
    }

    private Map<Long, List<TicketFreebieConfig>> loadConfigsByMerchId(List<OrderItem> orderItems) {
        List<Long> merchIds = orderItems.stream()
                .filter(orderItem -> orderItem.getMerchVariantItem() != null
                        && orderItem.getMerchVariantItem().getMerchVariant() != null
                        && orderItem.getMerchVariantItem().getMerchVariant().getMerch() != null
                        && orderItem.getMerchVariantItem().getMerchVariant().getMerch().getMerchType() == MerchType.TICKET
                        && Boolean.TRUE.equals(orderItem.getMerchVariantItem().getMerchVariant().getMerch().getHasFreebie()))
                .map(orderItem -> orderItem.getMerchVariantItem().getMerchVariant().getMerch().getMerchId())
                .distinct()
                .toList();
        if (merchIds.isEmpty()) {
            return Map.of();
        }

        return ticketFreebieConfigRepository
                .findByTicketMerchMerchIdInOrderByTicketMerchMerchIdAscDisplayOrderAscTicketFreebieConfigIdAsc(merchIds)
                .stream()
                .collect(Collectors.groupingBy(
                        config -> config.getTicketMerch().getMerchId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private OrderItem getOrderItem(Long orderItemId) {
        if (orderItemId == null || orderItemId <= 0) {
            throw new InvalidRequestException("Order item ID is required");
        }
        return orderItemRepository.findByIdWithStudentAndMerchDetails(orderItemId)
                .orElseThrow(() -> new OrderItemNotFoundException("Order item not found"));
    }

    private TicketFreebieAssignmentResponseDTO buildResponse(TicketFreebieConfig config, TicketFreebieAssignment assignment) {
        TicketFreebieAssignmentResponseDTO response = ticketFreebieAssignmentMapper.toResponseDTO(assignment);
        enrichResponse(response, config);
        return response;
    }

    private TicketFreebieAssignmentResponseDTO buildPendingResponse(TicketFreebieConfig config, Long orderItemId) {
        TicketFreebieAssignmentResponseDTO response = TicketFreebieAssignmentResponseDTO.builder()
                .orderItemId(orderItemId)
                .ticketFreebieConfigId(config.getTicketFreebieConfigId())
                .fulfillmentStatus(TicketFreebieFulfillmentStatus.PENDING_DETAILS)
                .build();
        enrichResponse(response, config);
        return response;
    }

    private TicketFreebieAssignmentResponseDTO buildNoFreebieResponse(Long orderItemId) {
        return TicketFreebieAssignmentResponseDTO.builder()
                .orderItemId(orderItemId)
                .hasFreebie(false)
                .fulfillmentStatus(TicketFreebieFulfillmentStatus.NO_FREEBIE)
                .build();
    }

    private List<TicketFreebieAssignmentResponseDTO> buildResponses(
            Long orderItemId,
            TicketContext ticketContext,
            List<TicketFreebieAssignment> assignments) {
        if (!ticketContext.hasFreebie()) {
            return List.of(buildNoFreebieResponse(orderItemId));
        }

        Map<Long, TicketFreebieAssignment> assignmentsByConfigId = assignments.stream()
                .filter(assignment -> assignment.getTicketFreebieConfig() != null
                        && assignment.getTicketFreebieConfig().getTicketFreebieConfigId() != null)
                .collect(Collectors.toMap(
                        assignment -> assignment.getTicketFreebieConfig().getTicketFreebieConfigId(),
                        assignment -> assignment));

        return ticketContext.configs().stream()
                .map(config -> {
                    TicketFreebieAssignment assignment = assignmentsByConfigId.get(config.getTicketFreebieConfigId());
                    return assignment == null
                            ? buildPendingResponse(config, orderItemId)
                            : buildResponse(config, assignment);
                })
                .toList();
    }

    private void enrichResponse(TicketFreebieAssignmentResponseDTO response, TicketFreebieConfig config) {
        response.setHasFreebie(true);
        response.setTicketFreebieConfigId(config.getTicketFreebieConfigId());
        response.setCategory(config.getCategory());
        response.setFreebieName(config.getFreebieName());
        response.setClothingSubtype(config.getClothingSubtype());
        response.setAllowedSizes(config.getSizeOptions().stream().map(option -> option.getSizeLabel()).toList());
        response.setAllowedColors(config.getColorOptions().stream().map(option -> option.getColorLabel()).toList());
        response.setAllowedDesigns(config.getDesignOptions().stream().map(option -> option.getDesignLabel()).toList());
    }

    private String normalizeOptionalText(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private record TicketContext(boolean hasFreebie, List<TicketFreebieConfig> configs) {
    }
}
