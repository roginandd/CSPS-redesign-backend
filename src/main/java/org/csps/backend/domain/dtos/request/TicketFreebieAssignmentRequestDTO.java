package org.csps.backend.domain.dtos.request;

import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.TicketFreebieFulfillmentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketFreebieAssignmentRequestDTO {
    private Long ticketFreebieConfigId;
    private ClothingSizing selectedSize;

    private String selectedColor;

    private String selectedDesign;

    private TicketFreebieFulfillmentStatus fulfillmentStatus;
}
