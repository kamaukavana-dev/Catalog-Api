package com.catalog.order.application;

import com.catalog.order.domain.Order;
import com.catalog.order.domain.OrderStatus;
import com.catalog.order.infrastructure.OrderRepository;
import com.catalog.variant.infrastructure.VariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private VariantRepository variantRepository;

    @InjectMocks private OrderService orderService;

    @Test
    void shouldCreateOrder_whenVariantsExist() {
        UUID customerId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        List<OrderService.CreateOrderItem> items = List.of(
                new OrderService.CreateOrderItem(variantId, 2, new BigDecimal("50.00"))
        );
        when(variantRepository.existsById(variantId)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order order = orderService.createOrder(customerId, items, "USD");

        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldCancelOrder_whenStatusIsPending() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), List.of(), BigDecimal.ZERO, "USD");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderService.cancelOrder(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldThrowException_whenCancellingShippedOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), List.of(), BigDecimal.ZERO, "USD");
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
            orderService.cancelOrder(orderId));
    }
}
