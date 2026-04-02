package org.csps.backend.domain.dtos.response;

import java.util.List;

import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.TicketFreebieCategory;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemFreebieSelectionItemResponseDTO {
    private Long ticketFreebieConfigId;
    private Integer displayOrder;
    private TicketFreebieCategory category;
    private String freebieName;
    private String clothingSubtype;
    private List<ClothingSizing> availableSizes;
    private List<String> availableColors;
    private List<String> availableDesigns;
    private ClothingSizing selectedSize;
    private String selectedColor;
    private String selectedDesign;
}
