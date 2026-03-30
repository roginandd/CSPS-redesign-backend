package org.csps.backend.domain.dtos.response.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDTO {
    private Long id;
    private Long orderId;         // Order ID for fetching associated order items
    private String studentId;
    private String studentName;
    private String idNumber;       // Student ID Number
    private String membershipType; // e.g., "Non-Member", "Member"
    private BigDecimal amount;
    private String date;           // ISO 8601 or formatted date string
    private String status;         // "PENDING", "CLAIMED", "REJECTED", "CANCELLED"
}
