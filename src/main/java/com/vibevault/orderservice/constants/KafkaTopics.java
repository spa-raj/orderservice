package com.vibevault.orderservice.constants;

public final class KafkaTopics {
    public static final String CART_EVENTS = "cart-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String PAYMENT_EVENTS = "payment-events";

    private KafkaTopics() {
    }
}
