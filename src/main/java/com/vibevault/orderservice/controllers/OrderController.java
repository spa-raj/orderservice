package com.vibevault.orderservice.controllers;

import com.vibevault.orderservice.dtos.order.OrderResponseDto;
import com.vibevault.orderservice.models.Order;
import com.vibevault.orderservice.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public Page<OrderResponseDto> getUserOrders(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        Page<Order> orders = orderService.getUserOrders(jwt.getSubject(), pageable);
        return orders.map(OrderResponseDto::fromOrder);
    }

    @GetMapping("/{orderId}")
    public OrderResponseDto getOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        Order order = orderService.getOrderById(orderId, jwt.getSubject());
        return OrderResponseDto.fromOrder(order);
    }
}
