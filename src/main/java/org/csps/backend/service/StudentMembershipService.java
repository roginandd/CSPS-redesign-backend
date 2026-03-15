package org.csps.backend.service;

import java.util.List;
import java.util.Optional;

import org.csps.backend.domain.dtos.request.BulkStudentMembershipRequestDTO;
import org.csps.backend.domain.dtos.request.StudentMembershipRequestDTO;
import org.csps.backend.domain.dtos.request.StudentMembershipSearchDTO;
import org.csps.backend.domain.dtos.response.MembershipRatioDTO;
import org.csps.backend.domain.dtos.response.StudentMembershipResponseDTO;
import org.csps.backend.domain.dtos.response.StudentNonMemberResponseDTO;
import org.csps.backend.domain.dtos.response.StudentResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StudentMembershipService {
    StudentMembershipResponseDTO createStudentMembership(StudentMembershipRequestDTO requestDTO);
    List<StudentMembershipResponseDTO> getAllStudentMemberships();
    List<StudentMembershipResponseDTO> getStudentWithMemberships(String studentId);
    Optional<StudentMembershipResponseDTO> getStudentMembershipById(Long membershipId);
    StudentMembershipResponseDTO updateStudentMembership(Long membershipId, StudentMembershipRequestDTO requestDTO);
    StudentMembershipResponseDTO getActiveMembershipByStudentId(String studentId);
    /**
     * Get all student memberships with pagination.
     * Default page size is 7 items per page.
     * @param pageable pagination details
     * @return paginated list of student memberships
     */
    Page<StudentMembershipResponseDTO> getAllStudentMembershipsPaginated(Pageable pageable);
    
    /**
     * Get student memberships for a specific student with pagination.
     * @param studentId the student ID
     * @param pageable pagination details
     * @return paginated list of memberships for the student
     */
    Page<StudentMembershipResponseDTO> getStudentMembershipsPaginated(String studentId, Pageable pageable);

    /**
     * Get all students with active memberships, paginated.
     * Uses EntityGraph to eager load student profile data.
     *
     * @param pageable pagination details
     * @return paginated list of active membership response DTOs
     */
    Page<StudentMembershipResponseDTO> getActiveMembersPaginated(Pageable pageable);

    /**
     * Get all students who do NOT have an active membership (non-members), paginated.
     * Uses a NOT IN subquery to exclude students with active memberships.
     *
     * @param pageable pagination details
     * @return paginated list of inactive non-member DTOs
     */
    Page<StudentNonMemberResponseDTO> getInactiveMembersPaginated(Pageable pageable);

    /**
     * Get count of currently active members.
     *
     * @return total number of active members
     */
    long getActiveMembersCount();

    /**
     * Get membership ratio: active members vs total students.
     * Uses existing MembershipRatioDTO for the response.
     *
     * @return membership ratio with total students, paid members, non-members, and percentage
     */
    MembershipRatioDTO getMembershipRatio();

    /**
     * Search memberships with dynamic filters using JPA Specification.
     * Supports filtering by student name, student ID, year level,
     * active status, academic year, and semester.
     *
     * @param searchDTO the search/filter criteria
     * @param pageable  pagination details
     * @return paginated list of StudentMembershipResponseDTO for active/all membership rows
     *         or StudentNonMemberResponseDTO when activeStatus = INACTIVE
     */
    Page<?> searchMemberships(StudentMembershipSearchDTO searchDTO, Pageable pageable);

    /**
     * Get ALL active members (unpaginated) for CSV export.
     *
     * @return complete list of active membership response DTOs
     */
    List<StudentMembershipResponseDTO> getAllActiveMembers();

    /**
     * Get ALL non-members (students without active membership, unpaginated) for CSV export.
     *
     * @return complete list of student response DTOs for non-members
     */
    List<StudentResponseDTO> getAllNonMembers();

    /**
     * Bulk create memberships for multiple students in a single academic year.
     * Duplicates and non-existent students are silently skipped.
     * Uses saveAll() to avoid N+1 queries.
     *
     * @param bulkRequestDTO contains list of student IDs and academic year
     * @return list of created membership response DTOs
     */
    List<StudentMembershipResponseDTO> bulkCreateMemberships(BulkStudentMembershipRequestDTO bulkRequestDTO);
}
