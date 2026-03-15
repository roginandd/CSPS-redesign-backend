package org.csps.backend.repository;

import java.util.List;
import java.util.Optional;

import org.csps.backend.domain.entities.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, String> {
    
    /* eagerly load userAccount and userProfile to avoid N+1 queries */
    @EntityGraph(attributePaths = {"userAccount", "userAccount.userProfile"})
    Page<Student> findAll(Pageable pageable);
    
    /* search students by studentId or name with optional year level filter */
    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.userAccount ua LEFT JOIN FETCH ua.userProfile up " +
           "WHERE (s.studentId LIKE CONCAT('%', :search, '%') OR " +
           "up.firstName LIKE CONCAT('%', :search, '%') OR " +
           "up.lastName LIKE CONCAT('%', :search, '%')) " +
           "AND (:yearLevel IS NULL OR s.yearLevel = :yearLevel)")
    Page<Student> searchStudents(@Param("search") String search, @Param("yearLevel") Byte yearLevel, Pageable pageable);
    
    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.userAccount ua LEFT JOIN FETCH ua.userProfile WHERE s.studentId = :studentId")
    Optional<Student> findByStudentId(@Param("studentId") String studentId);
    
    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.userAccount ua LEFT JOIN FETCH ua.userProfile WHERE ua.userAccountId = :accountId")
    Optional<Student> findByUserAccountUserAccountId(@Param("accountId") Long accountId);

    /* efficient query to get student ID for JWT generation without full entity fetch */
    @Query("SELECT s.studentId FROM Student s WHERE s.userAccount.userAccountId = :accountId")
    Optional<String> findStudentIdByUserAccountId(@Param("accountId") Long accountId);

    /**
     * Find all students who do NOT have an active membership (non-members).
     * Uses a NOT IN subquery to exclude students with at least one active StudentMembership.
     * Eager loads userAccount and userProfile via EntityGraph to avoid N+1 queries.
     *
     * @param pageable pagination details
     * @return paginated list of students without active memberships
     */
    @EntityGraph(attributePaths = {"userAccount", "userAccount.userProfile"})
    @Query("SELECT s FROM Student s WHERE s.studentId NOT IN " +
           "(SELECT sm.student.studentId FROM StudentMembership sm WHERE sm.active = true)")
    Page<Student> findStudentsWithoutActiveMembership(Pageable pageable);

    /**
     * Search students who do NOT have an active membership (non-members).
     * Supports the same studentId/name filters used by membership search.
     *
     * @param search generic partial match on student ID or name
     * @param studentName partial match on first name, last name, or full name
     * @param studentId partial match on student ID
     * @param pageable pagination details
     * @return paginated list of filtered students without active memberships
     */
    @EntityGraph(attributePaths = {"userAccount", "userAccount.userProfile"})
    @Query("SELECT s FROM Student s JOIN s.userAccount ua JOIN ua.userProfile up " +
           "WHERE s.studentId NOT IN " +
           "(SELECT sm.student.studentId FROM StudentMembership sm WHERE sm.active = true) " +
           "AND (:search IS NULL OR " +
           "LOWER(s.studentId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(up.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(up.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(CONCAT(up.firstName, CONCAT(' ', up.lastName))) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(CONCAT(up.lastName, CONCAT(' ', up.firstName))) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:studentName IS NULL OR " +
           "LOWER(up.firstName) LIKE LOWER(CONCAT('%', :studentName, '%')) OR " +
           "LOWER(up.lastName) LIKE LOWER(CONCAT('%', :studentName, '%')) OR " +
           "LOWER(CONCAT(up.firstName, CONCAT(' ', up.lastName))) LIKE LOWER(CONCAT('%', :studentName, '%')) OR " +
           "LOWER(CONCAT(up.lastName, CONCAT(' ', up.firstName))) LIKE LOWER(CONCAT('%', :studentName, '%'))) " +
           "AND (:studentId IS NULL OR LOWER(s.studentId) LIKE LOWER(CONCAT('%', :studentId, '%')))")
    Page<Student> searchStudentsWithoutActiveMembership(
            @Param("search") String search,
            @Param("studentName") String studentName,
            @Param("studentId") String studentId,
            Pageable pageable);

    /**
     * Find ALL students without active membership (unpaginated) with eager loading.
     * Used for CSV export of non-member data.
     *
     * @return full list of students without active memberships
     */
    @EntityGraph(attributePaths = {"userAccount", "userAccount.userProfile"})
    @Query("SELECT s FROM Student s WHERE s.studentId NOT IN " +
           "(SELECT sm.student.studentId FROM StudentMembership sm WHERE sm.active = true)")
    List<Student> findAllStudentsWithoutActiveMembership();
}
