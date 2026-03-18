package com.vibevault.orderservice.services;

import com.vibevault.orderservice.constants.KafkaTopics;
import com.vibevault.orderservice.events.PaymentEvent;
import com.vibevault.orderservice.models.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final OrderEventProducer orderEventProducer;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_EVENTS,
            groupId = "orderservice",
            containerFactory = "paymentEventListenerContainerFactory"
    )
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("Processing payment event {} type {} for order {}", event.getEventId(), event.getEventType(), event.getOrderId());

        try {
            switch (event.getEventType()) {
                case "PAYMENT_CONFIRMED" -> {
                    Order order = orderService.confirmOrder(event.getOrderId());
                    orderEventProducer.sendOrderConfirmed(order);
                }
                case "PAYMENT_FAILED" -> {
                    Order order = orderService.cancelOrder(event.getOrderId(), event.getFailureReason());
                    orderEventProducer.sendOrderCancelled(order);
                }
                default -> log.warn("Unknown payment event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process payment event {} for order {}: {}",
                    event.getEventId(), event.getOrderId(), e.getMessage(), e);
        }
    }
}
