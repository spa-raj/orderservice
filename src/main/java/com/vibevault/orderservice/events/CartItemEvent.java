package com.vibevault.orderservice.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemEvent {
    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal price;
    private String currency;
    private LocalDateTime addedAt;
}
