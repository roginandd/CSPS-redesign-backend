package org.csps.backend.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.csps.backend.domain.dtos.request.OrderSearchDTO;
import org.csps.backend.domain.dtos.response.sales.ChartPointDTO;
import org.csps.backend.domain.dtos.response.sales.SalesStatsDTO;
import org.csps.backend.domain.dtos.response.sales.TransactionDTO;
import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.entities.OrderItem;
import org.csps.backend.domain.entities.StudentMembership;
import org.csps.backend.domain.enums.MerchType;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.domain.enums.SalesPeriod;
import org.csps.backend.exception.InvalidOrderStatusTransitionException;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.OrderRepository;
import org.csps.backend.repository.StudentMembershipRepository;
import org.csps.backend.repository.specification.OrderSpecification;
import org.csps.backend.service.OrderLifecycleService;
import org.csps.backend.service.OrderNotificationService;
import org.csps.backend.service.SalesService;
import org.csps.backend.service.StudentMembershipService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SalesServiceImpl implements SalesService {

    private static final BigDecimal MEMBERSHIP_REVENUE_PER_MEMBER = BigDecimal.valueOf(100);
    private static final List<OrderStatus> FINANCE_REVENUE_STATUSES =
            List.of(OrderStatus.TO_BE_CLAIMED, OrderStatus.CLAIMED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderLifecycleService orderLifecycleService;
    private final StudentMembershipService studentMembershipService;
    private final StudentMembershipRepository studentMembershipRepository;
    private final OrderNotificationService orderNotificationService;

    @Value("${csps.currentAcademicYear.start}")
    private int currentYearStart;

    @Value("${csps.currentAcademicYear.end}")
    private int currentYearEnd;

    // Deployment date - set to today (February 8, 2026) as dummy data
    private static final LocalDate DEPLOYMENT_DATE = LocalDate.of(2026, 2, 25);

    @Override
    public SalesStatsDTO getSalesStats(SalesPeriod period) {
        List<Order> financeOrders = orderRepository.findByOrderStatusIn(FINANCE_REVENUE_STATUSES);
        List<StudentMembership> currentYearMemberships =
                studentMembershipRepository.findByYearStartAndYearEnd(currentYearStart, currentYearEnd);

        List<ChartPointDTO> chartData = generateChartData(financeOrders, currentYearMemberships, period);

        // Calculate total sales from chart data (sum of all chart values)
        BigDecimal totalSales = chartData.stream()
                .map(ChartPointDTO::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SalesStatsDTO.builder()
                .totalSales(totalSales)
                .currency("PHP")
                .chartData(chartData)
                .build();
    }

    @Override
    public Page<TransactionDTO> getTransactions(Pageable pageable, OrderSearchDTO searchDTO) {
        /* build specification for database-level filtering to prevent loading all orders into memory */
        OrderSearchDTO normalizedSearch = normalizeAndValidateSearch(searchDTO);
        Specification<Order> spec = OrderSpecification.withFilters(normalizedSearch);
        
        /* fetch paginated results with eager loading via @EntityGraph in OrderRepository.findAll(pageable) */
        Page<Order> orders = orderRepository.findAll(spec, pageable);
        Set<String> activeMembershipStudentIds = resolveActiveMembershipStudentIds(orders.getContent());

        /* map orders to DTOs */
        return orders.map(order -> mapToTransactionDTO(order, activeMembershipStudentIds.contains(order.getStudent().getStudentId())));
    }



    @Override
    @Transactional
    public TransactionDTO approveTransaction(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStatusTransitionException("Only pending orders can be approved");
        }

        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            throw new InvalidRequestException("Orders without items cannot be approved");
        }

        if (order.getOrderItems().stream().anyMatch(item -> item.getOrderStatus() != OrderStatus.PENDING)) {
            throw new InvalidOrderStatusTransitionException("Only orders with pending items can be approved");
        }

        if (containsMembershipItem(order.getOrderItems())) {
            studentMembershipService.ensureMembershipForCurrentAcademicYear(order.getStudent().getStudentId());
        }

        for (OrderItem item : order.getOrderItems()) {
            item.setOrderStatus(resolveApprovedItemStatus(item));
            item.setUpdatedAt(LocalDateTime.now());
            orderItemRepository.save(item);
        }

        order.setOrderStatus(resolveOrderStatusAfterApproval(order));
        order.setUpdatedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);
        sendApprovedItemNotifications(savedOrder.getOrderId());

        return mapToTransactionDTO(savedOrder, isActiveMember(savedOrder.getStudent().getStudentId()));
    }

    @Override
    @Transactional
    public void rejectTransaction(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderStatusTransitionException("Cancelled orders cannot be rejected");
        }

        orderLifecycleService.applyTerminalStatus(order, OrderStatus.REJECTED);
    }

    private TransactionDTO mapToTransactionDTO(Order order, boolean activeMember) {
        String studentName = order.getStudent().getUserAccount().getUserProfile().getFirstName() + " " +
                order.getStudent().getUserAccount().getUserProfile().getLastName();

        return TransactionDTO.builder()
                .id(order.getOrderId())
                .orderId(order.getOrderId())
                .studentId(order.getStudent().getStudentId())
                .studentName(studentName)
                .idNumber(order.getStudent().getStudentId())
                .membershipType(activeMember ? "Member" : "Non-Member")
                .amount(BigDecimal.valueOf(order.getTotalPrice()))
                .date(order.getOrderDate().toLocalDate().toString())
                .status(order.getOrderStatus().name())
                .build();
    }

    private Set<String> resolveActiveMembershipStudentIds(List<Order> orders) {
        Set<String> studentIds = orders.stream()
                .map(Order::getStudent)
                .filter(student -> student != null && student.getStudentId() != null)
                .map(student -> student.getStudentId().trim())
                .filter(studentId -> !studentId.isEmpty())
                .collect(Collectors.toSet());

        if (studentIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(studentMembershipRepository.findActiveStudentIdsByStudentIdIn(studentIds));
    }

    private boolean containsMembershipItem(List<OrderItem> orderItems) {
        return orderItems.stream().anyMatch(item -> item.getMerchVariantItem() != null
                && item.getMerchVariantItem().getMerchVariant() != null
                && item.getMerchVariantItem().getMerchVariant().getMerch() != null
                && item.getMerchVariantItem().getMerchVariant().getMerch().getMerchType() == MerchType.MEMBERSHIP);
    }

    private void sendApprovedItemNotifications(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderOrderId(orderId);
        for (OrderItem orderItem : orderItems) {
            var notificationData = orderNotificationService.extractNotificationData(orderItem, orderItem.getOrderStatus());
            if (notificationData != null) {
                orderNotificationService.sendOrderStatusEmail(notificationData);
            }
        }
    }

    private OrderStatus resolveApprovedItemStatus(OrderItem item) {
        if (item != null
                && item.getMerchVariantItem() != null
                && item.getMerchVariantItem().getMerchVariant() != null
                && item.getMerchVariantItem().getMerchVariant().getMerch() != null
                && item.getMerchVariantItem().getMerchVariant().getMerch().getMerchType() == MerchType.MEMBERSHIP) {
            return OrderStatus.CLAIMED;
        }

        return OrderStatus.TO_BE_CLAIMED;
    }

    private OrderStatus resolveOrderStatusAfterApproval(Order order) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return OrderStatus.PENDING;
        }

        boolean allClaimed = order.getOrderItems().stream().allMatch(item -> item.getOrderStatus() == OrderStatus.CLAIMED);
        return allClaimed ? OrderStatus.CLAIMED : OrderStatus.TO_BE_CLAIMED;
    }

    private boolean isActiveMember(String studentId) {
        return studentMembershipRepository.hasActiveMembership(studentId);
    }

    private List<ChartPointDTO> generateChartData(
            List<Order> orders,
            List<StudentMembership> memberships,
            SalesPeriod period) {
        Map<String, BigDecimal> groupedData = new LinkedHashMap<>();

        orders.forEach(order -> {
            if (order.getOrderDate() == null) {
                return;
            }

            LocalDate orderDate = order.getOrderDate().toLocalDate();
            BigDecimal merchRevenue = calculateFinanceMerchRevenue(order);
            if (merchRevenue.signum() == 0) {
                return;
            }

            String key = resolveChartKey(orderDate, period);
            groupedData.put(key, groupedData.getOrDefault(key, BigDecimal.ZERO).add(merchRevenue));
        });

        memberships.forEach(membership -> {
            if (membership.getDateJoined() == null) {
                return;
            }

            String key = resolveChartKey(membership.getDateJoined().toLocalDate(), period);
            groupedData.put(
                    key,
                    groupedData.getOrDefault(key, BigDecimal.ZERO).add(MEMBERSHIP_REVENUE_PER_MEMBER));
        });

        return groupedData.entrySet().stream()
                .map(entry -> ChartPointDTO.builder()
                        .label(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .collect(Collectors.toList());
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

    private String resolveChartKey(LocalDate date, SalesPeriod period) {
        return switch (period) {
            case DAILY -> date.toString();
            case WEEKLY -> {
                long daysSinceDeployment = java.time.temporal.ChronoUnit.DAYS.between(DEPLOYMENT_DATE, date);
                int weekSinceDeployment = (int) Math.ceil((daysSinceDeployment + 1.0) / 7.0);
                yield "Week " + Math.max(1, weekSinceDeployment);
            }
            case MONTHLY -> date.getMonth().toString() + " " + date.getYear();
            case YEARLY -> String.valueOf(date.getYear());
            case ALL_TIME -> "All Time";
        };
    }

    private OrderSearchDTO normalizeAndValidateSearch(OrderSearchDTO searchDTO) {
        OrderSearchDTO normalizedSearch = searchDTO == null ? new OrderSearchDTO() : searchDTO;

        normalizedSearch.setStudentName(normalizeWhitespace(normalizedSearch.getStudentName()));
        normalizedSearch.setStudentId(trimToNull(normalizedSearch.getStudentId()));
        normalizedSearch.setStatus(normalizeStatus(normalizedSearch.getStatus()));

        if (normalizedSearch.getStartDate() != null
                && normalizedSearch.getEndDate() != null
                && normalizedSearch.getEndDate().isBefore(normalizedSearch.getStartDate())) {
            throw new InvalidRequestException("End date must be greater than or equal to start date");
        }

        if (normalizedSearch.getStatus() != null) {
            try {
                OrderStatus.valueOf(normalizedSearch.getStatus());
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException("Invalid order status: " + normalizedSearch.getStatus());
            }
        }

        if (normalizedSearch.getYear() != null
                && (normalizedSearch.getYear() < 1000 || normalizedSearch.getYear() > 9999)) {
            throw new InvalidRequestException("Year filter must be a four-digit year");
        }

        return normalizedSearch;
    }

    private String normalizeStatus(String status) {
        String trimmedStatus = trimToNull(status);
        return trimmedStatus == null ? null : trimmedStatus.toUpperCase(Locale.ROOT);
    }

    private String normalizeWhitespace(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
