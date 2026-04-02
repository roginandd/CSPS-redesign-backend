package org.csps.backend.domain.dtos.request;

import java.util.List;

import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.TicketFreebieCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketFreebieConfigRequestDTO {
    private Long ticketFreebieConfigId;

    @NotNull(message = "Freebie category is required")
    private TicketFreebieCategory category;

    @NotBlank(message = "Freebie name is required")
    private String freebieName;

    private Integer displayOrder;

    private String clothingSubtype;

    private List<ClothingSizing> sizes;

    private List<String> colors;

    private List<String> designs;
}
