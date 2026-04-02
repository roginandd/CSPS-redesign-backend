package org.csps.backend.domain.dtos.response;

import java.time.LocalDateTime;
import java.util.List;

import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderItemResponseDTO {
    private Long orderItemId;
    private Long orderId;
    private String studentId;
    private String studentName;
    private String merchName;
    private String color;
    private String design;
    private String size;
    private Integer quantity;
    private Double totalPrice;
    private OrderStatus orderStatus;
    private String s3ImageKey;
    private MerchType merchType;
    private List<TicketFreebieAssignmentResponseDTO> freebieAssignments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
