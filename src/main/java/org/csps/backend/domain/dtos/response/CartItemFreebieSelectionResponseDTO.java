package org.csps.backend.domain.dtos.response;

import java.util.List;

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
public class CartItemFreebieSelectionResponseDTO {
    private Long merchVariantItemId;
    private Long merchId;
    private List<CartItemFreebieSelectionItemResponseDTO> freebies;
}
