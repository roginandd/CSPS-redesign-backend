package org.csps.backend.domain.dtos.request;

import java.util.ArrayList;
import java.util.List;

import org.csps.backend.domain.enums.MerchType;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MerchUpdateRequestDTO {
    private String merchName;
    private String description;
    private MerchType merchType;
    private Boolean hasFreebie;

    @Valid
    @Builder.Default
    private List<TicketFreebieConfigRequestDTO> freebieConfigs = new ArrayList<>();
}
