package org.csps.backend.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.csps.backend.domain.dtos.request.BulkStudentMembershipRequestDTO;
import org.csps.backend.domain.dtos.request.StudentMembershipRequestDTO;
import org.csps.backend.domain.dtos.request.StudentMembershipSearchDTO;
import org.csps.backend.domain.dtos.response.MembershipRatioDTO;
import org.csps.backend.domain.dtos.response.StudentMembershipResponseDTO;
import org.csps.backend.domain.dtos.response.StudentNonMemberResponseDTO;
import org.csps.backend.domain.dtos.response.StudentResponseDTO;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.entities.StudentMembership;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.MemberNotFoundException;
import org.csps.backend.exception.StudentNotFoundException;
import org.csps.backend.mapper.StudentMapper;
import org.csps.backend.mapper.StudentMembershipMapper;
import org.csps.backend.repository.StudentMembershipRepository;
import org.csps.backend.repository.StudentRepository;
import org.csps.backend.repository.specification.StudentMembershipSpecification;
import org.csps.backend.service.StudentMembershipService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Service implementation for managing student memberships.
 * Provides CRUD operations for StudentMembership entities.
 */
@Service
@RequiredArgsConstructor
public class StudentMembershipServiceImpl implements StudentMembershipService {

    private final StudentMembershipMapper studentMembershipMapper;
    private final StudentMembershipRepository studentMembershipRepository;
    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;

    @Value("${csps.currentAcademicYear.start}")   
    private int currentYearStart;

    @Value("${csps.currentAcademicYear.end}")
    private int currentYearEnd;

    /**
     * Creates a new student membership.
     *
     * @param requestDTO the request data for creating the membership
     * @return the created membership response DTO
     */
    @Override
    @Transactional
    public StudentMembershipResponseDTO createStudentMembership(@Valid StudentMembershipRequestDTO requestDTO) {
        // validate student exists
        String studentId = requestDTO.getStudentId();
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        // validate year range
        if (requestDTO.getYearStart() == null || requestDTO.getYearEnd() == null) {
            throw new InvalidRequestException("Year start and year end cannot be null");
        }

        if (requestDTO.getYearStart() >= requestDTO.getYearEnd()) {
            throw new InvalidRequestException("Year start must be less than year end");
        }

        // check if membership already exists for this year
        Optional<StudentMembership> existingMembership = studentMembershipRepository
                .findByStudentStudentIdAndYearStartAndYearEnd(studentId, requestDTO.getYearStart(), requestDTO.getYearEnd());
        if (existingMembership.isPresent()) {
            throw new InvalidRequestException("Membership already exists for academic year " + requestDTO.getYearStart() + "-" + requestDTO.getYearEnd());
        }

        // determine if membership should be active (only if it's the current academic year)
        boolean activeFlag = requestDTO.getYearStart() == currentYearStart && requestDTO.getYearEnd() == currentYearEnd;

        // map to entity
        StudentMembership membership = studentMembershipMapper.toEntity(requestDTO);
        membership.setStudent(student);
        membership.setYearStart(requestDTO.getYearStart());
        membership.setYearEnd(requestDTO.getYearEnd());
        membership.setActive(activeFlag);

        // save
        StudentMembership saved = studentMembershipRepository.save(membership);

        // return DTO
        return studentMembershipMapper.toResponseDTO(saved);
    }

    /**
     * Retrieves all student memberships.
     *
     * @return list of all membership response DTOs
     */
    @Override
    public List<StudentMembershipResponseDTO> getAllStudentMemberships() {
        return studentMembershipRepository.findAll().stream()
                .map(studentMembershipMapper::toResponseDTO)
                .toList();
    }

    /**
     * Retrieves memberships for a specific student.
     *
     * @param studentId the student ID
     * @return list of membership response DTOs
     */
    @Override
    public List<StudentMembershipResponseDTO> getStudentWithMemberships(String studentId) {
        /* verify student exists */
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException("Student not found with ID: " + studentId);
        }

        /* get memberships */
        List<StudentMembership> memberships = studentMembershipRepository.findByStudentStudentId(studentId);

        /* map to DTO */
        return memberships.stream()
                .map(studentMembershipMapper::toResponseDTO)
                .toList();
    }

    /**
     * Retrieves a student membership by ID.
     *
     * @param membershipId the membership ID
     * @return optional of membership response DTO
     */
    @Override
    public Optional<StudentMembershipResponseDTO> getStudentMembershipById(Long membershipId) {
        return studentMembershipRepository.findById(membershipId)
                .map(studentMembershipMapper::toResponseDTO);
    }

    /**
     * Updates an existing student membership.
     *
     * @param membershipId the membership ID to update
     * @param requestDTO the updated data
     * @return the updated membership response DTO
     */
    @Override
    @Transactional
    public StudentMembershipResponseDTO updateStudentMembership(Long membershipId, @Valid StudentMembershipRequestDTO requestDTO) {
        StudentMembership existing = studentMembershipRepository.findById(membershipId)
                .orElseThrow(() -> new MemberNotFoundException("Membership not found with ID: " + membershipId));

        /* update year range if provided */
        if (requestDTO.getYearStart() != null) {
            existing.setYearStart(requestDTO.getYearStart());
        }
        if (requestDTO.getYearEnd() != null) {
            existing.setYearEnd(requestDTO.getYearEnd());
        }

        /* if student changed, update it */
        if (!existing.getStudent().getStudentId().equals(requestDTO.getStudentId())) {
            String newStudentId = requestDTO.getStudentId();
            Student newStudent = studentRepository.findByStudentId(newStudentId)
                    .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + newStudentId));
            existing.setStudent(newStudent);
        }

        StudentMembership saved = studentMembershipRepository.save(existing);
        return studentMembershipMapper.toResponseDTO(saved);
    }


    /**
     * Retrieves the active membership for a student.
     *
     * @param studentId the student ID
     * @return the active membership response DTO, or null if not found
     */
    @Override
    public StudentMembershipResponseDTO getActiveMembershipByStudentId(String studentId) {
        return studentMembershipRepository.findByStudentStudentIdAndActive(studentId, true)
                .map(studentMembershipMapper::toResponseDTO)
                .orElse(null);
    }

    /**
     * Retrieves all student memberships with pagination (7 items per page by default).
     *
     * @param pageable the pagination details
     * @return paginated list of membership response DTOs
     */
    @Override
    public Page<StudentMembershipResponseDTO> getAllStudentMembershipsPaginated(Pageable pageable) {
        Page<StudentMembership> memberships = studentMembershipRepository.findAll(pageable);
        return memberships.map(studentMembershipMapper::toResponseDTO);
    }

    /**
     * Retrieves memberships for a specific student with pagination (7 items per page by default).
     *
     * @param studentId the student ID
     * @param pageable the pagination details
     * @return paginated list of membership response DTOs for the student
     */
    @Override
    public Page<StudentMembershipResponseDTO> getStudentMembershipsPaginated(String studentId, Pageable pageable) {
        // Verify student exists
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException("Student not found with ID: " + studentId);
        }
        
        Page<StudentMembership> memberships = studentMembershipRepository.findByStudentStudentId(studentId, pageable);
        return memberships.map(studentMembershipMapper::toResponseDTO);
    }

    /**
     * Retrieves all active student memberships with pagination.
     * Uses EntityGraph on repository to eagerly load student profile data.
     *
     * @param pageable the pagination details
     * @return paginated list of active membership response DTOs
     */
    @Override
    public Page<StudentMembershipResponseDTO> getActiveMembersPaginated(Pageable pageable) {
        Page<StudentMembership> activeMembers = studentMembershipRepository.findByActiveTrue(pageable);
        return activeMembers.map(studentMembershipMapper::toResponseDTO);
    }

    /**
     * Retrieves all students who do NOT have an active membership (non-members).
     * Delegates to StudentRepository's NOT IN subquery with EntityGraph.
     *
     * @param pageable the pagination details
     * @return paginated list of inactive non-member DTOs
     */
    @Override
    public Page<StudentNonMemberResponseDTO> getInactiveMembersPaginated(Pageable pageable) {
        Page<Student> nonMembers = studentRepository.findStudentsWithoutActiveMembership(pageable);
        return nonMembers.map(this::toNonMemberResponseDTO);
    }

    /**
     * Returns the count of currently active members.
     *
     * @return total number of active members
     */
    @Override
    public long getActiveMembersCount() {
        return studentMembershipRepository.countByActiveTrue();
    }

    /**
     * Calculates the membership ratio: active members vs total students.
     * Computes totalStudents, paidMembersCount, nonMembersCount, and memberPercentage.
     *
     * @return populated MembershipRatioDTO with all ratio metrics
     */
    @Override
    public MembershipRatioDTO getMembershipRatio() {
        long totalStudents = studentRepository.count();
        long activeMembers = studentMembershipRepository.countByActiveTrue();
        long nonMembers = totalStudents - activeMembers;

        MembershipRatioDTO ratio = new MembershipRatioDTO();
        ratio.setTotalStudents((int) totalStudents);
        ratio.setPaidMembersCount((int) activeMembers);
        ratio.setNonMembersCount((int) nonMembers);
        ratio.setMemberPercentage(totalStudents > 0 
            ? (double) activeMembers / totalStudents * 100 
            : 0.0);
        return ratio;
    }

    /**
     * Search memberships with dynamic filters using JPA Specification.
     * Builds predicates from the search DTO and delegates to JpaSpecificationExecutor.
     *
     * @param searchDTO the search/filter criteria (all fields optional)
     * @param pageable  pagination details
     * @return paginated list of matching membership response DTOs
     */
    @Override
    public Page<?> searchMemberships(StudentMembershipSearchDTO searchDTO, Pageable pageable) {
        if (isInactiveSearch(searchDTO)) {
            Page<Student> nonMembers = studentRepository.searchStudentsWithoutActiveMembership(
                    normalizeSearchValue(searchDTO.getSearch()),
                    normalizeSearchValue(searchDTO.getStudentName()),
                    normalizeSearchValue(searchDTO.getStudentId()),
                    pageable);
            return nonMembers.map(this::toNonMemberResponseDTO);
        }

        Specification<StudentMembership> spec = StudentMembershipSpecification.withFilters(searchDTO);
        Page<StudentMembership> memberships = studentMembershipRepository.findAll(spec, pageable);
        return memberships.map(studentMembershipMapper::toResponseDTO);
    }

    /**
     * Retrieves ALL active members without pagination for CSV export.
     * Uses the unpaginated findByActiveTrue() repository method.
     *
     * @return complete list of active membership response DTOs
     */
    @Override
    public List<StudentMembershipResponseDTO> getAllActiveMembers() {
        List<StudentMembership> activeMembers = studentMembershipRepository.findByActiveTrue();
        return activeMembers.stream()
                .map(studentMembershipMapper::toResponseDTO)
                .toList();
    }

    /**
     * Retrieves ALL non-members (students without active membership) without pagination for CSV export.
     * Uses the unpaginated findAllStudentsWithoutActiveMembership() repository method.
     *
     * @return complete list of student response DTOs for non-members
     */
    @Override
    public List<StudentResponseDTO> getAllNonMembers() {
        List<Student> nonMembers = studentRepository.findAllStudentsWithoutActiveMembership();
        return nonMembers.stream()
                .map(studentMapper::toResponseDTO)
                .toList();
    }

    /**
     * Bulk create memberships for multiple students in a single academic year.
     * Deduplicates student IDs and skips non-existent students.
     * Uses saveAll() to avoid N+1 queries.
     *
     * @param bulkRequestDTO contains list of student IDs and academic year range
     * @return list of created membership response DTOs
     */
    @Override
    @Transactional
    public List<StudentMembershipResponseDTO> bulkCreateMemberships(@Valid BulkStudentMembershipRequestDTO bulkRequestDTO) {
        /* validate year range */
        if (bulkRequestDTO.getYearStart() == null || bulkRequestDTO.getYearEnd() == null) {
            throw new InvalidRequestException("Year start and year end cannot be null");
        }

        if (bulkRequestDTO.getYearStart() >= bulkRequestDTO.getYearEnd()) {
            throw new InvalidRequestException("Year start must be less than year end");
        }

        /* deduplicate student IDs */
        Set<String> uniqueStudentIds = bulkRequestDTO.getStudentIds().stream()
                .distinct()
                .collect(Collectors.toSet());

        /* fetch all students that exist */
        List<Student> existingStudents = studentRepository.findAllById(uniqueStudentIds);

        if (existingStudents.isEmpty()) {
            throw new StudentNotFoundException("No valid students found for the provided IDs");
        }


        Set<String> existingStudentIds = existingStudents.stream()
                .map(Student::getStudentId)
                .collect(Collectors.toSet());

        /* determine if membership should be active */
        boolean shouldBeActive = bulkRequestDTO.getYearStart().equals(currentYearStart) 
                && bulkRequestDTO.getYearEnd().equals(currentYearEnd);

        /* check for existing memberships for this year range and filter them out */
        Set<String> alreadyHasMembership = studentMembershipRepository
                .findByStudentStudentIdInAndYearStartAndYearEnd(existingStudentIds, 
                        bulkRequestDTO.getYearStart(), bulkRequestDTO.getYearEnd())
                .stream()
                .map(m -> m.getStudent().getStudentId())
                .collect(Collectors.toSet());

        /* build list of students to create memberships for (exclude those who already have it) */
        List<StudentMembership> membershipsToCreate = existingStudents.stream()
                .filter(student -> !alreadyHasMembership.contains(student.getStudentId()))
                .map(student -> StudentMembership.builder()
                        .student(student)
                        .yearStart(bulkRequestDTO.getYearStart())
                        .yearEnd(bulkRequestDTO.getYearEnd())
                        .active(shouldBeActive)
                        .build())
                .toList();

        /* bulk save all memberships at once (avoids N+1 queries) */
        List<StudentMembership> savedMemberships = studentMembershipRepository.saveAll(membershipsToCreate);

        /* map to response DTOs */
        return savedMemberships.stream()
                .map(studentMembershipMapper::toResponseDTO)
                .toList();
    }

    private boolean isInactiveSearch(StudentMembershipSearchDTO searchDTO) {
        return searchDTO != null
                && searchDTO.getActiveStatus() != null
                && "INACTIVE".equalsIgnoreCase(searchDTO.getActiveStatus().trim());
    }

    private String normalizeSearchValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    private StudentNonMemberResponseDTO toNonMemberResponseDTO(Student student) {
        return StudentNonMemberResponseDTO.builder()
                .studentId(student.getStudentId())
                .fullName(buildFullName(student))
                .build();
    }

    private String buildFullName(Student student) {
        if (student == null || student.getUserAccount() == null || student.getUserAccount().getUserProfile() == null) {
            return null;
        }

        List<String> nameParts = Stream.of(
                student.getUserAccount().getUserProfile().getFirstName(),
                student.getUserAccount().getUserProfile().getMiddleName(),
                student.getUserAccount().getUserProfile().getLastName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .toList();

        return nameParts.isEmpty() ? null : String.join(" ", nameParts);
    }
}
