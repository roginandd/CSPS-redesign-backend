package org.csps.backend.domain.dtos.response;

import java.time.LocalDateTime;
import java.util.List;

import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.TicketFreebieCategory;
import org.csps.backend.domain.enums.TicketFreebieFulfillmentStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketFreebieAssignmentResponseDTO {
    private Long ticketFreebieAssignmentId;
    private Long orderItemId;
    private Long ticketFreebieConfigId;
    private Boolean hasFreebie;
    private TicketFreebieCategory category;
    private String freebieName;
    private String clothingSubtype;
    private List<ClothingSizing> allowedSizes;
    private List<String> allowedColors;
    private List<String> allowedDesigns;
    private ClothingSizing selectedSize;
    private String selectedColor;
    private String selectedDesign;
    private TicketFreebieFulfillmentStatus fulfillmentStatus;
    private LocalDateTime claimedAt;
    private LocalDateTime fulfilledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
