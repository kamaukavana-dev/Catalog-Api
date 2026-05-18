package com.catalog.order.domain;

import com.catalog.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "customer_id")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "USD";

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineItem> items = new ArrayList<>();

    @Column(name = "payment_reference", length = 200)
    private String paymentReference;

    public static Order create(UUID customerId, List<OrderLineItem> items, BigDecimal totalAmount, String currency) {
        Order o = new Order();
        o.setCustomerId(customerId);
        o.setStatus(OrderStatus.PENDING);
        o.setTotalAmount(totalAmount);
        o.setCurrency(currency == null ? "USD" : currency);
        if (items != null) {
            items.forEach(i -> o.addItem(i));
        }
        return o;
    }

    public void addItem(OrderLineItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    public void transitionTo(OrderStatus target) {
        this.status.assertCanTransitionTo(target);
        this.setStatus(target);
    }

    public boolean isCancellable() {
        return this.status == OrderStatus.PENDING || this.status == OrderStatus.PAID;
    }
}

