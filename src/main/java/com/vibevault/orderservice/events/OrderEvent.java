package com.vibevault.orderservice.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {
    private String eventId;
    private OrderEventType eventType;
    private UUID orderId;
    private String userId;
    private BigDecimal totalAmount;
    private String currency;
    private List<CartItemEvent> items;
    private LocalDateTime timestamp;
}
