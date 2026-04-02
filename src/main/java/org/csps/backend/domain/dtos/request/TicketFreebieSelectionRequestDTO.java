package org.csps.backend.domain.dtos.request;

import org.csps.backend.domain.enums.ClothingSizing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketFreebieSelectionRequestDTO {
    private Long ticketFreebieConfigId;
    private ClothingSizing selectedSize;
    private String selectedColor;
    private String selectedDesign;
}
