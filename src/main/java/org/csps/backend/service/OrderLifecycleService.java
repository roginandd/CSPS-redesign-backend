package org.csps.backend.service;

import org.csps.backend.domain.entities.Order;
import org.csps.backend.domain.enums.OrderStatus;

public interface OrderLifecycleService {

    /**
     * Move an order and its items to a terminal status and restore reserved stock.
     */
    Order applyTerminalStatus(Order order, OrderStatus targetStatus);
}
