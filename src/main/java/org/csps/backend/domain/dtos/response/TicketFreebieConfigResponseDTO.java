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
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketFreebieConfigResponseDTO {
    private Long ticketFreebieConfigId;
    private Integer displayOrder;
    private TicketFreebieCategory category;
    private String freebieName;
    private String clothingSubtype;
    private List<ClothingSizing> sizes;
    private List<String> colors;
    private List<String> designs;
}
