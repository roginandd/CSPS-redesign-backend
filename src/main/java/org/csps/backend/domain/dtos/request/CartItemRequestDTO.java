package org.csps.backend.domain.dtos.request;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItemRequestDTO {
    private Long merchVariantItemId;
    private int quantity;

    @Valid
    @Builder.Default
    private List<TicketFreebieSelectionRequestDTO> freebieSelections = new ArrayList<>();
}
