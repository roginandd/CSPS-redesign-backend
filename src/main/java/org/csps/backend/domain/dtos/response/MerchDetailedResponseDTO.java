package org.csps.backend.domain.dtos.response;

import java.util.List;

import org.csps.backend.domain.enums.MerchType;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class MerchDetailedResponseDTO {
    private Long merchId;
    private String merchName;
    private String description;
    private MerchType merchType;
    private Double basePrice;
    private String s3ImageKey;
    private Boolean hasFreebie;
    private List<TicketFreebieConfigResponseDTO> freebieConfigs;
    private Boolean purchaseBlocked;
    private String purchaseBlockMessage;

    @JsonAlias("variants")
    private List<MerchVariantResponseDTO> variants;
}
