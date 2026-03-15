package org.csps.backend.domain.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentNonMemberResponseDTO {
    private String studentId;
    private String fullName;
}
