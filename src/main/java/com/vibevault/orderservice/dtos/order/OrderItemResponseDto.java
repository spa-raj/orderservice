package com.vibevault.orderservice.dtos.order;

import com.vibevault.orderservice.models.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponseDto {
    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal price;
    private String currency;

    public static OrderItemResponseDto fromOrderItem(OrderItem item) {
        return OrderItemResponseDto.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .currency(item.getCurrency())
                .build();
    }
}
