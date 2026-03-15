package org.csps.backend.domain.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Search filter DTO for querying student memberships with JPA Specification.
 * All fields are optional — null fields are ignored in the predicate build.
 *
 * @field search       generic partial match on student ID or name
 * @field studentName  partial match on first name, last name, or full name
 * @field studentId    partial match on student ID
 * @field activeStatus "ACTIVE" for membership rows, "INACTIVE" for non-members, or null for all memberships
 * @field yearStart    filter by membership academic year start
 * @field yearEnd      filter by membership academic year end
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentMembershipSearchDTO {

    private String search;

    private String studentName;

    private String studentId;

    private String activeStatus;

    private Integer yearStart;

    private Integer yearEnd;
}
