package org.csps.backend.domain.dtos.response;

import java.util.List;

import org.csps.backend.domain.enums.MerchType;

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
public class MerchSummaryResponseDTO {
    private Long merchId;
    private String merchName;
    private String description;
    private MerchType merchType;
    private Double basePrice;
    private String s3ImageKey;
    private Integer totalStockQuantity;
    private Boolean hasFreebie;
    private List<TicketFreebieConfigResponseDTO> freebieConfigs;
    private Boolean purchaseBlocked;
    private String purchaseBlockMessage;

    public MerchSummaryResponseDTO(Long merchId, String merchName, String description, MerchType merchType,
            Double basePrice, String s3ImageKey, Integer totalStockQuantity, Boolean hasFreebie) {
        this.merchId = merchId;
        this.merchName = merchName;
        this.description = description;
        this.merchType = merchType;
        this.basePrice = basePrice;
        this.s3ImageKey = s3ImageKey;
        this.totalStockQuantity = totalStockQuantity;
        this.hasFreebie = hasFreebie;
    }
}
