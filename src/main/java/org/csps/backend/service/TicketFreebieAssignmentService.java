package org.csps.backend.service;

import java.util.List;
import java.util.Map;

import org.csps.backend.domain.dtos.request.TicketFreebieAssignmentRequestDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieAssignmentResponseDTO;

public interface TicketFreebieAssignmentService {
    /** Initialize or update ticket freebie assignments when a new order item is created. */
    List<TicketFreebieAssignmentResponseDTO> initializeAssignments(Long orderItemId, List<TicketFreebieAssignmentRequestDTO> requests);

    /** Get the effective freebie assignment view for an order item. */
    List<TicketFreebieAssignmentResponseDTO> getAssignmentsByOrderItemId(Long orderItemId);

    /** Bulk-load effective freebie assignment views for order items. */
    Map<Long, List<TicketFreebieAssignmentResponseDTO>> getAssignmentsByOrderItemIds(List<Long> orderItemIds);

    /** Admin backfill/edit endpoint for an order item's freebie assignments. */
    List<TicketFreebieAssignmentResponseDTO> upsertAssignments(Long orderItemId, List<TicketFreebieAssignmentRequestDTO> requests);
}
