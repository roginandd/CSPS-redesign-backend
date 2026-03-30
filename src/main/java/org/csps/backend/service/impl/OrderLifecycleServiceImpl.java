package org.csps.backend.service.impl;

import java.time.LocalDateTime;

import org.csps.backend.domain.entities.MerchVariantItem;
import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.entities.OrderItem;
import org.csps.backend.domain.enums.OrderStatus;
import org.csps.backend.exception.InvalidOrderStatusTransitionException;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.repository.MerchVariantItemRepository;
import org.csps.backend.repository.OrderItemRepository;
import org.csps.backend.repository.OrderRepository;
import org.csps.backend.service.OrderLifecycleService;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderLifecycleServiceImpl implements OrderLifecycleService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MerchVariantItemRepository merchVariantItemRepository;

    @Override
    @Transactional
    public Order applyTerminalStatus(Order order, OrderStatus targetStatus) {
        if (order == null || order.getOrderId() == null) {
            throw new InvalidRequestException("Order is required");
        }

        if (targetStatus != OrderStatus.REJECTED && targetStatus != OrderStatus.CANCELLED) {
            throw new InvalidOrderStatusTransitionException("Unsupported terminal order status: " + targetStatus);
        }

        LocalDateTime now = LocalDateTime.now();

        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                if (item == null) {
                    continue;
                }

                if (shouldRestoreStock(item.getOrderStatus())) {
                    MerchVariantItem lockedItem = merchVariantItemRepository.findByIdWithLock(
                            item.getMerchVariantItem().getMerchVariantItemId())
                            .orElseThrow(() -> new InvalidRequestException(
                                    "MerchVariantItem not found during stock restoration"));

                    lockedItem.setStockQuantity(lockedItem.getStockQuantity() + item.getQuantity());
                    merchVariantItemRepository.save(lockedItem);
                }

                if (item.getOrderStatus() != targetStatus) {
                    item.setOrderStatus(targetStatus);
                    item.setUpdatedAt(now);
                    orderItemRepository.save(item);
                }
            }
        }

        order.setOrderStatus(targetStatus);
        order.setUpdatedAt(now);
        return orderRepository.save(order);
    }

    private boolean shouldRestoreStock(OrderStatus currentStatus) {
        return currentStatus != OrderStatus.REJECTED && currentStatus != OrderStatus.CANCELLED;
    }
}
