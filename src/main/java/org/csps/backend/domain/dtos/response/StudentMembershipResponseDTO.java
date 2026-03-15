package org.csps.backend.domain.dtos.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentMembershipResponseDTO {
    private Long membershipId;
    private String studentId;
    private String fullName;

    private LocalDateTime dateJoined;

    private boolean active;

    @JsonAlias("year_start")
    private int yearStart;

    @JsonAlias("year_end")
    private int yearEnd;

    public String getAcademicYearRange() {
        return yearStart + "-" + yearEnd;
    }
}
