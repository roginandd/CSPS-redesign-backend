package org.csps.backend.repository.specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.csps.backend.domain.dtos.request.OrderSearchDTO;
import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.domain.entities.UserProfile;
import org.csps.backend.domain.enums.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;


@Component
public class OrderSpecification {
    
    /**
     * build a dynamic specification for order filtering
     * applies predicates based on non-null search criteria in ordersearchdto
     * supports filtering by student name, student id, status, and date range
     */
    public static Specification<Order> withFilters(OrderSearchDTO searchDTO) {
        return withFilters(searchDTO, null);
    }

    public static Specification<Order> withFilters(OrderSearchDTO searchDTO, String enforcedStudentId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            OrderSearchDTO safeSearch = Objects.nonNull(searchDTO) ? searchDTO : new OrderSearchDTO();

            Join<Order, Student> studentJoin = root.join("student", JoinType.LEFT);
            Join<Student, UserAccount> userAccountJoin = studentJoin.join("userAccount", JoinType.LEFT);
            Join<UserAccount, UserProfile> userProfileJoin = userAccountJoin.join("userProfile", JoinType.LEFT);

            if (Objects.nonNull(safeSearch.getStudentName()) && !safeSearch.getStudentName().isBlank()) {
                String nameLike = "%" + normalizeWhitespace(safeSearch.getStudentName()) + "%";
                Expression<String> fullName = normalizedExpression(
                        cb,
                        cb.concat(
                                cb.concat(userProfileJoin.get("firstName"), " "),
                                cb.concat(
                                        cb.concat(cb.coalesce(userProfileJoin.get("middleName"), ""), " "),
                                        userProfileJoin.get("lastName"))));
                Expression<String> firstAndLastName = normalizedExpression(
                        cb,
                        cb.concat(
                                cb.concat(userProfileJoin.get("firstName"), " "),
                                userProfileJoin.get("lastName")));

                predicates.add(cb.or(
                        cb.like(fullName, nameLike),
                        cb.like(firstAndLastName, nameLike)));
            }

            if ((Objects.isNull(enforcedStudentId) || enforcedStudentId.isBlank())
                    && Objects.nonNull(safeSearch.getStudentId())
                    && !safeSearch.getStudentId().isBlank()) {
                predicates.add(cb.equal(studentJoin.get("studentId"), safeSearch.getStudentId().trim()));
            }

            if (Objects.nonNull(enforcedStudentId) && !enforcedStudentId.isBlank()) {
                predicates.add(cb.equal(studentJoin.get("studentId"), enforcedStudentId.trim()));
            }

            if (Objects.nonNull(safeSearch.getStatus()) && !safeSearch.getStatus().isBlank()) {
                OrderStatus status = OrderStatus.valueOf(safeSearch.getStatus().trim().toUpperCase());
                predicates.add(cb.equal(root.get("orderStatus"), status));
            }

            if (Objects.nonNull(safeSearch.getYear())) {
                LocalDateTime startOfYear = LocalDateTime.of(safeSearch.getYear(), 1, 1, 0, 0);
                LocalDateTime startOfNextYear = startOfYear.plusYears(1);
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), startOfYear));
                predicates.add(cb.lessThan(root.get("orderDate"), startOfNextYear));
            }

            if (Objects.nonNull(safeSearch.getStartDate())) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), safeSearch.getStartDate()));
            }

            if (Objects.nonNull(safeSearch.getEndDate())) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), safeSearch.getEndDate()));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Expression<String> normalizedExpression(CriteriaBuilder cb, Expression<String> expression) {
        Expression<String> normalized = cb.lower(cb.trim(expression));
        normalized = cb.function("replace", String.class, normalized, cb.literal("  "), cb.literal(" "));
        normalized = cb.function("replace", String.class, normalized, cb.literal("  "), cb.literal(" "));
        return normalized;
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
