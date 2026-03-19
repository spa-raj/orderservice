package com.vibevault.orderservice.controllers;

import com.vibevault.orderservice.exceptions.OrderNotFoundException;
import com.vibevault.orderservice.models.Order;
import com.vibevault.orderservice.models.OrderStatus;
import com.vibevault.orderservice.security.SecurityConfig;
import com.vibevault.orderservice.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private Order buildOrder(UUID id, String userId) {
        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("1999.98"))
                .currency("INR")
                .build();
        order.setId(id);
        return order;
    }

    @Test
    void getUserOrders_returnsPagedResults() throws Exception {
        Order order = buildOrder(UUID.randomUUID(), "user-1");
        when(orderService.getUserOrders(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        mockMvc.perform(get("/orders")
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void getOrder_returnsOrderDetails() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "user-1");
        when(orderService.getOrderById(orderId, "user-1")).thenReturn(order);

        mockMvc.perform(get("/orders/{id}", orderId)
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getOrder_notFound_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrderById(orderId, "user-1"))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/orders/{id}", orderId)
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrders_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }
}
