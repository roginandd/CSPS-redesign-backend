package org.csps.backend.mapper;

import org.csps.backend.domain.dtos.request.StudentMembershipRequestDTO;
import org.csps.backend.domain.dtos.response.StudentMembershipResponseDTO;
import org.csps.backend.domain.entities.StudentMembership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StudentMembershipMapper {

    /* request DTO → entity */
    @Mapping(target = "membershipId", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "dateJoined", ignore = true)
    @Mapping(target = "active", ignore = true)
    StudentMembership toEntity(StudentMembershipRequestDTO requestDTO);

    /* entity → response DTO */
    @Mapping(target = "studentId", source = "student.studentId")
    @Mapping(target = "fullName", expression = "java(getFullName(membership))")
    StudentMembershipResponseDTO toResponseDTO(StudentMembership membership);

    default String getFullName(StudentMembership membership) {
        if (membership == null
                || membership.getStudent() == null
                || membership.getStudent().getUserAccount() == null
                || membership.getStudent().getUserAccount().getUserProfile() == null) {
            return null;
        }

        String firstName = membership.getStudent().getUserAccount().getUserProfile().getFirstName();
        String middleName = membership.getStudent().getUserAccount().getUserProfile().getMiddleName();
        String lastName = membership.getStudent().getUserAccount().getUserProfile().getLastName();

        StringBuilder fullName = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            fullName.append(firstName.trim());
        }
        if (middleName != null && !middleName.isBlank()) {
            if (fullName.length() > 0) {
                fullName.append(' ');
            }
            fullName.append(middleName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (fullName.length() > 0) {
                fullName.append(' ');
            }
            fullName.append(lastName.trim());
        }

        return fullName.isEmpty() ? null : fullName.toString();
    }
}
