package com.vibevault.orderservice.services;

import com.vibevault.orderservice.events.CartEvent;
import com.vibevault.orderservice.events.CartItemEvent;
import com.vibevault.orderservice.exceptions.OrderNotFoundException;
import com.vibevault.orderservice.models.Order;
import com.vibevault.orderservice.models.OrderStatus;
import com.vibevault.orderservice.repositories.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private CartEvent buildCheckoutEvent(String eventId, String userId) {
        CartItemEvent item = CartItemEvent.builder()
                .productId("prod-1")
                .productName("Test Product")
                .quantity(2)
                .price(new BigDecimal("999.99"))
                .currency("INR")
                .build();

        return CartEvent.builder()
                .eventId(eventId)
                .eventType("CHECKOUT_INITIATED")
                .userId(userId)
                .items(List.of(item))
                .timestamp(LocalDateTime.now())
                .build();
    }

    private Order buildOrder(UUID id, String userId, OrderStatus status) {
        Order order = Order.builder()
                .userId(userId)
                .status(status)
                .totalAmount(new BigDecimal("1999.98"))
                .currency("INR")
                .cartEventId("event-1")
                .build();
        order.setId(id);
        return order;
    }

    // --- createOrderFromCheckout ---

    @Test
    void createOrderFromCheckout_happyPath() {
        CartEvent event = buildCheckoutEvent("event-1", "user-1");
        when(orderRepository.findByCartEventId("event-1")).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        Order result = orderService.createOrderFromCheckout(event);

        assertNotNull(result.getId());
        assertEquals("user-1", result.getUserId());
        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertEquals(new BigDecimal("1999.98"), result.getTotalAmount());
        assertEquals(1, result.getItems().size());
        assertEquals("Test Product", result.getItems().get(0).getProductName());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrderFromCheckout_duplicateEvent_returnsExisting() {
        UUID existingId = UUID.randomUUID();
        Order existing = buildOrder(existingId, "user-1", OrderStatus.PENDING);
        CartEvent event = buildCheckoutEvent("event-1", "user-1");

        when(orderRepository.findByCartEventId("event-1")).thenReturn(Optional.of(existing));

        Order result = orderService.createOrderFromCheckout(event);

        assertEquals(existingId, result.getId());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderFromCheckout_nullItems_throwsException() {
        CartEvent event = CartEvent.builder()
                .eventId("event-1")
                .eventType("CHECKOUT_INITIATED")
                .userId("user-1")
                .items(null)
                .timestamp(LocalDateTime.now())
                .build();
        when(orderRepository.findByCartEventId("event-1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrderFromCheckout(event));
    }

    // --- confirmOrder ---

    @Test
    void confirmOrder_pendingToConfirmed() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "user-1", OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.confirmOrder(orderId);

        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void confirmOrder_alreadyConfirmed_noOp() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "user-1", OrderStatus.CONFIRMED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Order result = orderService.confirmOrder(orderId);

        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(orderRepository, never()).save(any());
    }

    // --- cancelOrder ---

    @Test
    void cancelOrder_pendingToCancelled() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "user-1", OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.cancelOrder(orderId, "payment failed");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrder_alreadyCancelled_noOp() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "user-1", OrderStatus.CANCELLED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Order result = orderService.cancelOrder(orderId, "payment failed");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(orderRepository, never()).save(any());
    }

    // --- getOrderById ---

    @Test
    void getOrderById_ownerAccess_returnsOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "user-1", OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderById(orderId, "user-1");

        assertEquals(orderId, result.getId());
    }

    @Test
    void getOrderById_nonOwner_throwsNotFound() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "user-1", OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrderById(orderId, "user-2"));
    }

    @Test
    void getOrderById_notFound_throwsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.getOrderById(orderId, "user-1"));
    }

    // --- getUserOrders ---

    @Test
    void getUserOrders_returnsPaginatedResults() {
        Order order = buildOrder(UUID.randomUUID(), "user-1", OrderStatus.PENDING);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1", PageRequest.of(0, 10)))
                .thenReturn(page);

        Page<Order> result = orderService.getUserOrders("user-1", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("user-1", result.getContent().get(0).getUserId());
    }
}
