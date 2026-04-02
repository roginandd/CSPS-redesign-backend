package org.csps.backend.domain.dtos.response;

import java.time.LocalDateTime;
import java.util.List;

import org.csps.backend.domain.enums.ClothingSizing;
import org.csps.backend.domain.enums.OrderStatus;

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
public class MerchCustomerResponseDTO {
    private Long orderItemId;
    private String studentId;
    private String studentName;
    private Byte yearLevel;
    private String merchName;
    private String color;
    private String design;
    private ClothingSizing size;
    private Integer quantity;
    private Double totalPrice;
    private OrderStatus orderStatus;
    private LocalDateTime orderDate;
    private String s3ImageKey;
    private Boolean hasFreebie;
    private List<TicketFreebieAssignmentResponseDTO> freebieAssignments;
}
