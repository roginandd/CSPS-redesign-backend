package org.csps.backend.controller;

import java.util.List;
import java.util.Map;

import org.csps.backend.domain.dtos.request.BulkStudentMembershipRequestDTO;
import org.csps.backend.domain.dtos.request.StudentMembershipRequestDTO;
import org.csps.backend.domain.dtos.request.StudentMembershipSearchDTO;
import org.csps.backend.domain.dtos.response.MembershipRatioDTO;
import org.csps.backend.domain.dtos.response.StudentMembershipResponseDTO;
import org.csps.backend.domain.dtos.response.StudentNonMemberResponseDTO;
import org.csps.backend.domain.dtos.response.StudentResponseDTO;
import org.csps.backend.service.StudentMembershipService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/student-memberships")
@RequiredArgsConstructor
public class StudentMembershipController {

    private final StudentMembershipService studentMembershipService;

    @PostMapping()
    @PreAuthorize("hasRole('ADMIN_EXECUTIVE') or hasRole('ADMIN_FINANCE')")
    public ResponseEntity<StudentMembershipResponseDTO> createStudentMembership(@RequestBody StudentMembershipRequestDTO studentMembershipRequestDTO) {
        StudentMembershipResponseDTO createdMembership = studentMembershipService.createStudentMembership(studentMembershipRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMembership);
    }

    @GetMapping()
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StudentMembershipResponseDTO>> getAllStudentMemberships() {
        List<StudentMembershipResponseDTO> memberships = studentMembershipService.getAllStudentMemberships();
        return ResponseEntity.ok(memberships);
    }

    @GetMapping("/{membershipId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentMembershipResponseDTO> getStudentMembership(@PathVariable Long membershipId) {
        StudentMembershipResponseDTO membership = studentMembershipService.getStudentMembershipById(membershipId)
                                                .orElseThrow(() -> new RuntimeException("Membership not found with ID: " + membershipId));
        return ResponseEntity.ok(membership);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StudentMembershipResponseDTO>> getStudentWithMemberships(@PathVariable String studentId) {
        List<StudentMembershipResponseDTO> response = studentMembershipService.getStudentWithMemberships(studentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<StudentMembershipResponseDTO>> getAllStudentMembershipsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StudentMembershipResponseDTO> memberships = studentMembershipService.getAllStudentMembershipsPaginated(pageable);
        return ResponseEntity.ok(memberships);
    }

    @GetMapping("/student/{studentId}/paginated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<StudentMembershipResponseDTO>> getStudentMembershipsPaginated(
            @PathVariable String studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StudentMembershipResponseDTO> memberships = studentMembershipService.getStudentMembershipsPaginated(studentId, pageable);
        return ResponseEntity.ok(memberships);
    }

    /**
     * Get all students with active memberships, paginated.
     * Returns membership details with student profile info.
     *
     * @param page zero-based page index (default 0)
     * @param size number of items per page (default 7)
     * @return paginated list of active membership response DTOs
     */
    @GetMapping("/active/paginated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<StudentMembershipResponseDTO>> getActiveMembersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StudentMembershipResponseDTO> activeMembers = studentMembershipService.getActiveMembersPaginated(pageable);
        return ResponseEntity.ok(activeMembers);
    }

    /**
     * Get all students who do NOT have an active membership (non-members), paginated.
     * Returns student profile info without membership details.
     *
     * @param page zero-based page index (default 0)
     * @param size number of items per page (default 7)
     * @return paginated list of inactive non-member DTOs
     */
    @GetMapping("/inactive/paginated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<StudentNonMemberResponseDTO>> getInactiveMembersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StudentNonMemberResponseDTO> inactiveMembers = studentMembershipService.getInactiveMembersPaginated(pageable);
        return ResponseEntity.ok(inactiveMembers);
    }

    /**
     * Get count of active members.
     * Lightweight endpoint, no full page fetch.
     *
     * @return JSON object with "count" key
     */
    @GetMapping("/active/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getActiveMembersCount() {
        long count = studentMembershipService.getActiveMembersCount();
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Get membership ratio (active vs total students).
     * Returns totalStudents, paidMembersCount, nonMembersCount, and memberPercentage.
     *
     * @return populated MembershipRatioDTO
     */
    @GetMapping("/ratio")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MembershipRatioDTO> getMembershipRatio() {
        MembershipRatioDTO ratio = studentMembershipService.getMembershipRatio();
        return ResponseEntity.ok(ratio);
    }

    /**
     * Search memberships or non-members depending on activeStatus.
     * All query parameters are optional — omitted params are ignored in the filter.
     *
     * @param search       generic partial match on student ID or name
     * @param studentName  partial match on first name, last name, or full name
     * @param studentId    partial match on student ID
     * @param activeStatus "ACTIVE" or omitted for membership rows, "INACTIVE" for non-members
     * @param yearStart    filter by membership academic year start
     * @param yearEnd      filter by membership academic year end
     * @param page         zero-based page index (default 0)
     * @param size         items per page (default 7)
     * @return paginated list of StudentMembershipResponseDTO or StudentNonMemberResponseDTO
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<?>> searchMemberships(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String activeStatus,
            @RequestParam(required = false) Integer yearStart,
            @RequestParam(required = false) Integer yearEnd,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size) {

        StudentMembershipSearchDTO searchDTO = StudentMembershipSearchDTO.builder()
                .search(search)
                .studentName(studentName)
                .studentId(studentId)
                .activeStatus(activeStatus)
                .yearStart(yearStart)
                .yearEnd(yearEnd)
                .build();

        Pageable pageable = PageRequest.of(page, size);
        Page<?> results = studentMembershipService.searchMemberships(searchDTO, pageable);
        return ResponseEntity.ok(results);
    }

    /**
     * Get ALL active members (unpaginated) for CSV export.
     * Returns the full list so frontend can convert to CSV.
     *
     * @return complete list of active membership response DTOs
     */
    @GetMapping("/active/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StudentMembershipResponseDTO>> exportActiveMembers() {
        List<StudentMembershipResponseDTO> activeMembers = studentMembershipService.getAllActiveMembers();
        return ResponseEntity.ok(activeMembers);
    }

    /**
     * Get ALL non-members (students without active membership, unpaginated) for CSV export.
     * Returns the full list so frontend can convert to CSV.
     *
     * @return complete list of student response DTOs for non-members
     */
    @GetMapping("/inactive/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StudentResponseDTO>> exportNonMembers() {
        List<StudentResponseDTO> nonMembers = studentMembershipService.getAllNonMembers();
        return ResponseEntity.ok(nonMembers);
    }

    /**
     * Bulk create memberships for multiple students in a single academic year.
     * Deduplicates and skips non-existent students silently.
     * Uses saveAll() to avoid N+1 queries.
     *
     * @param bulkRequestDTO contains list of student IDs and academic year range
     * @return list of created membership response DTOs
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN_FINANCE')")
    public ResponseEntity<List<StudentMembershipResponseDTO>> bulkCreateMemberships(
            @RequestBody BulkStudentMembershipRequestDTO bulkRequestDTO) {
        List<StudentMembershipResponseDTO> createdMemberships = studentMembershipService.bulkCreateMemberships(bulkRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMemberships);
    }
}
