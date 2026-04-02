package org.csps.backend.service;

import java.util.List;

import org.csps.backend.domain.dtos.request.TicketFreebieConfigRequestDTO;
import org.csps.backend.domain.dtos.response.TicketFreebieConfigResponseDTO;
import org.csps.backend.domain.entities.Merch;

public interface TicketFreebieConfigService {
    /** Get all configured freebies for a ticket merch. */
    List<TicketFreebieConfigResponseDTO> getConfigsByTicketMerchId(Long ticketMerchId);

    /** Get all configured freebies for a ticket merch variant item. */
    List<TicketFreebieConfigResponseDTO> getConfigsByMerchVariantItemId(Long merchVariantItemId);

    /** Create, replace, or remove ticket freebie configs during merch create/update. */
    void syncConfigsForMerch(Merch merch, Boolean hasFreebie, List<TicketFreebieConfigRequestDTO> requests);
}
