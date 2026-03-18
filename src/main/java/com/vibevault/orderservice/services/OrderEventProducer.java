package com.vibevault.orderservice.services;

import com.vibevault.orderservice.constants.KafkaTopics;
import com.vibevault.orderservice.events.CartItemEvent;
import com.vibevault.orderservice.events.OrderEvent;
import com.vibevault.orderservice.events.OrderEventType;
import com.vibevault.orderservice.models.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void sendOrderCreated(Order order) {
        send(buildEvent(order, OrderEventType.ORDER_CREATED));
    }

    public void sendOrderConfirmed(Order order) {
        send(buildEvent(order, OrderEventType.ORDER_CONFIRMED));
    }

    public void sendOrderCancelled(Order order) {
        send(buildEvent(order, OrderEventType.ORDER_CANCELLED));
    }

    private OrderEvent buildEvent(Order order, OrderEventType eventType) {
        return OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .orderId(order.getId())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .items(order.getItems().stream()
                        .map(item -> CartItemEvent.builder()
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .currency(item.getCurrency())
                                .build())
                        .toList())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void send(OrderEvent event) {
        try {
            kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, event.getUserId(), event);
            log.debug("Order event sent: {} for order {}", event.getEventType(), event.getOrderId());
        } catch (Exception e) {
            log.warn("Failed to send order event {} for order {}: {}",
                    event.getEventType(), event.getOrderId(), e.getMessage());
        }
    }
}
