package org.csps.backend.domain.dtos.request;

import org.csps.backend.domain.enums.OrderStatus;

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
    
    @NotNull(message = "Order ID is required")
    private Long orderId;
    
    private Long merchVariantId;

    @NotNull(message = "MerchVariantItem ID is required")
    private Long merchVariantItemId;
    
    @Min(value = 1, message = "Quantity must be at least 1")
    @NotNull(message = "Quantity is required")
    private Integer quantity;

    private OrderStatus orderStatus;
    
    private Double priceAtPurchase;
}
