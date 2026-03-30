package org.csps.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.csps.backend.domain.entities.StudentMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentMembershipRepository extends JpaRepository<StudentMembership, Long>, JpaSpecificationExecutor<StudentMembership> {
    
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    List<StudentMembership> findByStudentStudentId(String studentId);
    
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    Optional<StudentMembership> findByStudentStudentIdAndActive(String studentId, boolean isActive);
    
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    Optional<StudentMembership> findByStudentStudentIdAndYearStartAndYearEnd(String studentId, int yearStart, int yearEnd);
    
    /* eager load student and related user profile for dashboard use */
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    List<StudentMembership> findTop5ByOrderByDateJoinedDesc();
    
    long countByActiveTrue();
    
    @Override
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<StudentMembership> findAll(Pageable pageable);
    
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<StudentMembership> findByStudentStudentId(String studentId, Pageable pageable);

    /**
     * Find all active student memberships with eager loading and pagination.
     * Used by admin to list all currently active members.
     *
     * @param pageable pagination details
     * @return paginated list of active student memberships
     */
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    Page<StudentMembership> findByActiveTrue(Pageable pageable);

    /* check if student has active membership */
    @Query("SELECT CASE WHEN COUNT(sm) > 0 THEN true ELSE false END FROM StudentMembership sm WHERE sm.student.studentId = :studentId AND sm.active = true")
    boolean hasActiveMembership(@Param("studentId") String studentId);

    @Query("SELECT DISTINCT sm.student.studentId FROM StudentMembership sm WHERE sm.active = true AND sm.student.studentId IN :studentIds")
    List<String> findActiveStudentIdsByStudentIdIn(@Param("studentIds") Collection<String> studentIds);

    /**
     * Find ALL active student memberships (unpaginated) with eager loading.
     * Used for CSV export of active member data.
     *
     * @return full list of active student memberships
     */
    @EntityGraph(attributePaths = {"student", "student.userAccount", "student.userAccount.userProfile"}, type = EntityGraph.EntityGraphType.FETCH)
    List<StudentMembership> findByActiveTrue();

    /**
     * Find memberships for a set of student IDs in a specific academic year range.
     * Used for bulk creation to detect existing memberships and avoid duplicates.
     *
     * @param studentIds set of student IDs to search for
     * @param yearStart the academic year start
     * @param yearEnd the academic year end
     * @return list of existing memberships for the given students and year range
     */
    @EntityGraph(attributePaths = {"student"}, type = EntityGraph.EntityGraphType.FETCH)
    List<StudentMembership> findByStudentStudentIdInAndYearStartAndYearEnd(
            java.util.Set<String> studentIds, int yearStart, int yearEnd);
}
