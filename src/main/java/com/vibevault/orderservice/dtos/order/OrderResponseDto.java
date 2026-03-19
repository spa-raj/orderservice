package com.vibevault.orderservice.dtos.order;

import com.vibevault.orderservice.models.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponseDto {
    private UUID orderId;
    private String userId;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private List<OrderItemResponseDto> items;
    private Date createdAt;
    private Date updatedAt;

    public static OrderResponseDto fromOrder(Order order) {
        List<OrderItemResponseDto> itemDtos = order.getItems().stream()
                .map(OrderItemResponseDto::fromOrderItem)
                .toList();

        return OrderResponseDto.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .items(itemDtos)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getLastModifiedAt())
                .build();
    }
}
