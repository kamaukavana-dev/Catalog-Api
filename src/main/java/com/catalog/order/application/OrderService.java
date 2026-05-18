package com.catalog.order.application;

import com.catalog.order.domain.Order;
import com.catalog.order.domain.OrderLineItem;
import com.catalog.order.domain.OrderStatus;
import com.catalog.order.infrastructure.OrderRepository;
import com.catalog.variant.infrastructure.VariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final VariantRepository variantRepository;

    public OrderService(OrderRepository orderRepository, VariantRepository variantRepository) {
        this.orderRepository = orderRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional
    public Order createOrder(UUID customerId, List<CreateOrderItem> items, String currency) {
        // Validate variants exist and compute total
        List<OrderLineItem> lineItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItem it : items) {
            UUID variantId = it.variantId();
            // Ensure variant exists
            if (!variantRepository.existsById(variantId)) {
                throw new IllegalArgumentException("Variant not found: " + variantId);
            }
            BigDecimal unitPrice = it.unitPrice();
            int qty = it.quantity();
            OrderLineItem li = OrderLineItem.of(variantId, qty, unitPrice);
            lineItems.add(li);
            total = total.add(li.getTotalPrice());
        }

        Order order = Order.create(customerId, lineItems, total, currency);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Optional<Order> getById(UUID id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public Order updateStatus(UUID orderId, OrderStatus target) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        order.transitionTo(target);
        return orderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.isCancellable()) {
            throw new IllegalStateException("Order is not cancellable in status: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    public static record CreateOrderItem(UUID variantId, int quantity, BigDecimal unitPrice) {}
}

