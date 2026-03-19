package com.vibevault.orderservice.services;

import com.vibevault.orderservice.events.CartEvent;
import com.vibevault.orderservice.events.CartItemEvent;
import com.vibevault.orderservice.exceptions.OrderNotFoundException;
import com.vibevault.orderservice.models.Order;
import com.vibevault.orderservice.models.OrderItem;
import com.vibevault.orderservice.models.OrderStatus;
import com.vibevault.orderservice.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public Order createOrderFromCheckout(CartEvent event) {
        Optional<Order> existing = orderRepository.findByCartEventId(event.getEventId());
        if (existing.isPresent()) {
            log.warn("Duplicate cart event {} — returning existing order {}", event.getEventId(), existing.get().getId());
            return existing.get();
        }

        Order order = Order.builder()
                .userId(event.getUserId())
                .status(OrderStatus.PENDING)
                .cartEventId(event.getEventId())
                .currency("INR")
                .build();

        if (event.getItems() == null || event.getItems().isEmpty()) {
            throw new IllegalArgumentException("CHECKOUT_INITIATED event " + event.getEventId() + " has no items");
        }

        BigDecimal total = BigDecimal.ZERO;

        for (CartItemEvent item : event.getItems()) {
            OrderItem orderItem = OrderItem.builder()
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .currency(item.getCurrency() != null ? item.getCurrency() : "INR")
                    .build();
            order.addItem(orderItem);
            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        log.info("Order {} created for user {} from cart event {}", saved.getId(), saved.getUserId(), event.getEventId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(String userId, Pageable pageable) {
        return orderRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(UUID orderId, String userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    @Override
    @Transactional
    public Order confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order {} already confirmed — no-op", orderId);
            return order;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order cancelOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order {} already cancelled — no-op", orderId);
            return order;
        }
        log.info("Cancelling order {} — reason: {}", orderId, reason);
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }
}
