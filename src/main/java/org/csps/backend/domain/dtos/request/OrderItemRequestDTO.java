package org.csps.backend.domain.dtos.request;

import java.util.ArrayList;
import java.util.List;

import org.csps.backend.domain.enums.OrderStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderItemRequestDTO {
    private Long orderId;

    private Long merchVariantId;

    @NotNull(message = "MerchVariantItem ID is required")
    private Long merchVariantItemId;

    @Min(value = 1, message = "Quantity must be at least 1")
    @NotNull(message = "Quantity is required")
    private Integer quantity;

    private OrderStatus orderStatus;

    private Double priceAtPurchase;

    @Valid
    @Builder.Default
    private List<TicketFreebieSelectionRequestDTO> freebieSelections = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<TicketFreebieAssignmentRequestDTO> freebieAssignments = new ArrayList<>();
}
