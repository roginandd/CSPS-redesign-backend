package org.csps.backend.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.csps.backend.domain.dtos.response.ChartDataDTO;
import org.csps.backend.domain.dtos.response.FinanceDashboardDTO;
import org.csps.backend.domain.dtos.response.InventorySummaryDTO;
import org.csps.backend.domain.dtos.response.MembershipRatioDTO;
import org.csps.backend.domain.dtos.response.OrderSummaryDTO;
import org.csps.backend.domain.dtos.response.StudentMembershipDTO;
import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.entities.OrderItem;
import org.csps.backend.domain.entities.StudentMembership;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.repository.MerchVariantItemRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.OrderRepository;
import org.csps.backend.repository.StudentMembershipRepository;
import org.csps.backend.repository.StudentRepository;
import org.csps.backend.service.FinanceDashboardService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinanceDashboardServiceImpl implements FinanceDashboardService {

    private static final BigDecimal MEMBERSHIP_REVENUE_PER_MEMBER = BigDecimal.valueOf(100);
    private static final List<OrderStatus> FINANCE_REVENUE_STATUSES =
            List.of(OrderStatus.TO_BE_CLAIMED, OrderStatus.CLAIMED);

    private final MerchVariantItemRepository merchVariantItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final StudentMembershipRepository studentMembershipRepository;
    private final StudentRepository studentRepository;
    private final OrderRepository orderRepository;

    @Value("${csps.currentAcademicYear.start}")
    private int currentYearStart;

    @Value("${csps.currentAcademicYear.end}")
    private int currentYearEnd;

    @Override
    public FinanceDashboardDTO getFinanceDashboardData() {
        FinanceDashboardDTO dto = new FinanceDashboardDTO();

        dto.setInventory(getInventorySummary());
        dto.setRecentOrders(getRecentOrders());
        dto.setRecentMemberships(getRecentMemberships());
        dto.setMembershipRatio(getMembershipRatio());
        dto.setChartData(getChartData());

        return dto;
    }

    private List<InventorySummaryDTO> getInventorySummary() {
        // Get top 5 items with lowest stock
        return merchVariantItemRepository.findTop5ByOrderByStockQuantityAsc().stream()
                .map(item -> {
                    InventorySummaryDTO dto = new InventorySummaryDTO();
                    dto.setId(item.getMerchVariantItemId());
                    dto.setName(item.getMerchVariant().getMerch().getMerchName() + " - " + item.getMerchVariant().getMerch().getMerchName());
                    dto.setStock(item.getStockQuantity());
                    dto.setS3ImageKey(item.getMerchVariant().getS3ImageKey());
                    dto.setStockStatus(determineStockStatus(item.getStockQuantity()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<OrderSummaryDTO> getRecentOrders() {
        return orderItemRepository
                .findTop5ByOrderStatusInAndMerchVariantItemMerchVariantMerchMerchTypeNotOrderByCreatedAtDesc(
                        FINANCE_REVENUE_STATUSES,
                        MerchType.MEMBERSHIP)
                .stream()
                .map(item -> {
                    OrderSummaryDTO dto = new OrderSummaryDTO();
                    dto.setOrderItemId(item.getOrderItemId());
                    dto.setOrderId(item.getOrder().getOrderId());
                    dto.setStudentName(item.getOrder().getStudent().getUserAccount().getUserProfile().getFirstName() + " " + item.getOrder().getStudent().getUserAccount().getUserProfile().getLastName());
                    dto.setReferenceNumber("ORD-" + item.getOrder().getOrderId());
                    dto.setProductName(item.getMerchVariantItem().getMerchVariant().getMerch().getMerchName());
                    dto.setS3ImageKey(item.getMerchVariantItem().getMerchVariant().getS3ImageKey());
                    dto.setStatus(item.getOrderStatus().name());
                    dto.setPrice(calculateLineTotal(item));
                    dto.setCreatedAt(item.getCreatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<StudentMembershipDTO> getRecentMemberships() {
        // Get top 5 recent memberships
        return studentMembershipRepository.findTop5ByOrderByDateJoinedDesc().stream()
                .map(membership -> {
                    StudentMembershipDTO dto = new StudentMembershipDTO();
                    dto.setStudentId(membership.getStudent().getStudentId());
                    dto.setFullName(membership.getStudent().getUserAccount().getUserProfile().getFirstName() + " " + membership.getStudent().getUserAccount().getUserProfile().getLastName());
                    dto.setIdNumber(membership.getStudent().getStudentId());
                    dto.setIsPaid(membership.isActive());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private MembershipRatioDTO getMembershipRatio() {
        long totalStudents = studentRepository.count();
        long paidMembers = studentMembershipRepository.countByActiveTrue();
        long nonMembers = totalStudents - paidMembers;

        MembershipRatioDTO dto = new MembershipRatioDTO();
        dto.setTotalStudents((int) totalStudents);
        dto.setPaidMembersCount((int) paidMembers);
        dto.setNonMembersCount((int) nonMembers);

        double percentage = totalStudents > 0
        ? (double) paidMembers / totalStudents * 100
        : 0.0;

        percentage = Math.round(percentage * 100.0) / 100.0;

        dto.setMemberPercentage(percentage);


        return dto;
    }

    private ChartDataDTO getChartData() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Order> orders = orderRepository.findByOrderDateBetweenAndOrderStatusIn(
                startDateTime,
                endDateTime,
                FINANCE_REVENUE_STATUSES);
        List<StudentMembership> currentYearMemberships =
                studentMembershipRepository.findByYearStartAndYearEnd(currentYearStart, currentYearEnd);

        List<String> days = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                .collect(Collectors.toList());

        List<Integer> weeklyOrders = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> (int) orders.stream()
                        .filter(order -> order.getOrderDate() != null)
                        .filter(order -> order.getOrderDate().toLocalDate().equals(date))
                        .count())
                .collect(Collectors.toList());

        List<Double> weeklyRevenue = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> calculateMerchRevenueForDate(orders, date)
                        .add(calculateMembershipRevenueForDate(currentYearMemberships, date))
                        .doubleValue())
                .collect(Collectors.toList());

        ChartDataDTO dto = new ChartDataDTO();
        dto.setWeeklyOrders(weeklyOrders);
        dto.setWeeklyRevenue(weeklyRevenue);
        dto.setDays(days);

        return dto;
    }

    private String determineStockStatus(Integer stock) {
        if (stock == null || stock <= 0) return "OUT_OF_STOCK";
        if (stock <= 10) return "LOW_STOCK";
        return "IN_STOCK";
    }

    private BigDecimal calculateMerchRevenueForDate(List<Order> orders, LocalDate targetDate) {
        return orders.stream()
                .filter(order -> order.getOrderDate() != null)
                .filter(order -> order.getOrderDate().toLocalDate().equals(targetDate))
                .map(this::calculateFinanceMerchRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateMembershipRevenueForDate(List<StudentMembership> memberships, LocalDate targetDate) {
        long membershipCount = memberships.stream()
                .filter(membership -> membership.getDateJoined() != null)
                .filter(membership -> membership.getDateJoined().toLocalDate().equals(targetDate))
                .count();

        return MEMBERSHIP_REVENUE_PER_MEMBER.multiply(BigDecimal.valueOf(membershipCount));
    }

    private BigDecimal calculateFinanceMerchRevenue(Order order) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return order.getOrderItems().stream()
                .filter(this::isFinanceRevenueItem)
                .filter(item -> !isMembershipItem(item))
                .map(this::calculateLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isFinanceRevenueItem(OrderItem item) {
        return item != null
                && item.getOrderStatus() != null
                && FINANCE_REVENUE_STATUSES.contains(item.getOrderStatus());
    }

    private boolean isMembershipItem(OrderItem item) {
        return item != null
                && item.getMerchVariantItem() != null
                && item.getMerchVariantItem().getMerchVariant() != null
                && item.getMerchVariantItem().getMerchVariant().getMerch() != null
                && item.getMerchVariantItem().getMerchVariant().getMerch().getMerchType() == MerchType.MEMBERSHIP;
    }

    private BigDecimal calculateLineTotal(OrderItem item) {
        if (item == null || item.getPriceAtPurchase() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(item.getPriceAtPurchase())
                .multiply(BigDecimal.valueOf(item.getQuantity().longValue()));
    }
}
