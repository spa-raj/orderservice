package com.vibevault.orderservice.services;

import com.vibevault.orderservice.constants.KafkaTopics;
import com.vibevault.orderservice.events.CartEvent;
import com.vibevault.orderservice.models.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartEventConsumer {

    private final OrderService orderService;
    private final OrderEventProducer orderEventProducer;

    @KafkaListener(
            topics = KafkaTopics.CART_EVENTS,
            groupId = "orderservice",
            containerFactory = "cartEventListenerContainerFactory"
    )
    public void handleCartEvent(CartEvent event) {
        if (!"CHECKOUT_INITIATED".equals(event.getEventType())) {
            log.debug("Ignoring cart event type: {}", event.getEventType());
            return;
        }

        log.info("Processing CHECKOUT_INITIATED event {} for user {}", event.getEventId(), event.getUserId());

        try {
            Order order = orderService.createOrderFromCheckout(event);
            orderEventProducer.sendOrderCreated(order);
        } catch (Exception e) {
            log.error("Failed to process CHECKOUT_INITIATED event {} for user {}: {}",
                    event.getEventId(), event.getUserId(), e.getMessage(), e);
        }
    }
}
