package com.vibevault.orderservice.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartEvent {
    private String eventId;
    private String eventType;
    private String userId;
    private String productId;
    private Integer quantity;
    private List<CartItemEvent> items;
    private LocalDateTime timestamp;
}
