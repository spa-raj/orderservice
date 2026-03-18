package com.vibevault.orderservice.services;

import com.vibevault.orderservice.events.CartEvent;
import com.vibevault.orderservice.models.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {
    Order createOrderFromCheckout(CartEvent event);

    Page<Order> getUserOrders(String userId, Pageable pageable);

    Order getOrderById(UUID orderId, String userId);

    Order confirmOrder(UUID orderId);

    Order cancelOrder(UUID orderId, String reason);
}
