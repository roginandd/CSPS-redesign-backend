package org.csps.backend.domain.dtos.request;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderSearchDTO {
    
    private String studentName;
    
    private String studentId;
    
    private String status;

    private Integer year;
    
    private LocalDateTime startDate;
    
    private LocalDateTime endDate;
    
    private String sortBy;
    
    private String sortDirection;
}
