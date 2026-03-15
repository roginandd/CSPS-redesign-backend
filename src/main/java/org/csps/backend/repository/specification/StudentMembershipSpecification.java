package org.csps.backend.repository.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.csps.backend.domain.dtos.request.StudentMembershipSearchDTO;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.entities.StudentMembership;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.domain.entities.UserProfile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * JPA Specification builder for StudentMembership search/filter.
 * Follows the same pattern as OrderSpecification.
 * Builds dynamic predicates based on non-null fields in StudentMembershipSearchDTO.
 *
 * Joins are created conditionally:
 * - studentName filter: joins StudentMembership → Student → UserAccount → UserProfile
 * - studentId filter: joins StudentMembership → Student
 * - Other filters (status, year) operate on root StudentMembership entity without joins
 */
@Component
public class StudentMembershipSpecification {

    private static boolean hasText(String value) {
        return Objects.nonNull(value) && !value.trim().isEmpty();
    }

    private static Predicate buildNamePredicate(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Join<UserAccount, UserProfile> userProfileJoin,
            String searchValue) {
        String normalizedSearch = "%" + searchValue.toLowerCase().trim() + "%";

        return cb.or(
                cb.like(cb.lower(userProfileJoin.get("firstName")), normalizedSearch),
                cb.like(cb.lower(userProfileJoin.get("lastName")), normalizedSearch),
                cb.like(
                        cb.lower(
                                cb.concat(
                                        cb.concat(userProfileJoin.get("firstName"), " "),
                                        userProfileJoin.get("lastName"))),
                        normalizedSearch),
                cb.like(
                        cb.lower(
                                cb.concat(
                                        cb.concat(userProfileJoin.get("lastName"), " "),
                                        userProfileJoin.get("firstName"))),
                        normalizedSearch));
    }

    /**
     * Build a dynamic Specification for membership filtering.
     * Applies predicates based on non-null search criteria.
     * Supports filtering by generic search, student name, student ID,
     * active status, academic year start, and academic year end.
     *
     * @param searchDTO the search criteria DTO
     * @return composed Specification with all applicable filters
     */
    public static Specification<StudentMembership> withFilters(StudentMembershipSearchDTO searchDTO) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (Objects.nonNull(searchDTO)) {
                // Conditionally join only when needed to filter by student properties
                Join<StudentMembership, Student> studentJoin = null;
                Join<Student, UserAccount> userAccountJoin = null;
                Join<UserAccount, UserProfile> userProfileJoin = null;

                if (hasText(searchDTO.getSearch()) || hasText(searchDTO.getStudentName()) || hasText(searchDTO.getStudentId())) {
                    if (studentJoin == null) {
                        studentJoin = root.join("student", JoinType.LEFT);
                    }
                }

                if (hasText(searchDTO.getSearch()) || hasText(searchDTO.getStudentName())) {
                    if (userAccountJoin == null) {
                        userAccountJoin = studentJoin.join("userAccount", JoinType.LEFT);
                    }
                    if (userProfileJoin == null) {
                        userProfileJoin = userAccountJoin.join("userProfile", JoinType.LEFT);
                    }
                }

                // Filter by a generic search term (student ID or name)
                if (hasText(searchDTO.getSearch())) {
                    String searchLike = "%" + searchDTO.getSearch().toLowerCase().trim() + "%";
                    predicates.add(
                        cb.or(
                            cb.like(cb.lower(studentJoin.get("studentId").as(String.class)), searchLike),
                            buildNamePredicate(cb, userProfileJoin, searchDTO.getSearch())
                        )
                    );
                }

                // Filter by student name (first name, last name, or full name, case-insensitive partial match)
                if (hasText(searchDTO.getStudentName())) {
                    predicates.add(buildNamePredicate(cb, userProfileJoin, searchDTO.getStudentName()));
                }

                // Filter by exact student ID
                if (hasText(searchDTO.getStudentId())) {
                    predicates.add(cb.like(cb.lower(studentJoin.get("studentId").as(String.class)),
                            "%" + searchDTO.getStudentId().toLowerCase().trim() + "%"));
                }

                // Filter by active status ("ACTIVE" or "INACTIVE")
                if (hasText(searchDTO.getActiveStatus())) {
                    String status = searchDTO.getActiveStatus().trim().toUpperCase();
                    if ("ACTIVE".equals(status)) {
                        predicates.add(cb.equal(root.get("active"), true));
                    } else if ("INACTIVE".equals(status)) {
                        predicates.add(cb.equal(root.get("active"), false));
                    }
                    // Invalid status values are silently ignored
                }

                // Filter by academic year start
                if (Objects.nonNull(searchDTO.getYearStart())) {
                    predicates.add(cb.equal(root.get("yearStart"), searchDTO.getYearStart()));
                }

                // Filter by academic year end
                if (Objects.nonNull(searchDTO.getYearEnd())) {
                    predicates.add(cb.equal(root.get("yearEnd"), searchDTO.getYearEnd()));
                }
            }

            return cb.and(predicates.stream().toArray(Predicate[]::new));
        };
    }
}
