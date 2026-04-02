package org.csps.backend.domain.dtos.response;

import java.util.List;

import org.csps.backend.domain.enums.MerchType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponseDTO {
    private Long merchVariantItemId;
    private String merchName;
    private String size;
    private String color;
    private String design;
    private String s3ImageKey;
    private Double unitPrice;
    private int quantity;
    private Double subTotal;
    private MerchType merchType;
    private Boolean hasFreebie;
    private List<CartItemFreebieSelectionItemResponseDTO> freebieSelections;
}
