package com.catalog.order.domain;

import com.catalog.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "order_line_items")
public class OrderLineItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "total_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalPrice;

    public static OrderLineItem of(UUID variantId, int quantity, BigDecimal unitPrice) {
        OrderLineItem li = new OrderLineItem();
        li.setVariantId(variantId);
        li.setQuantity(quantity);
        li.setUnitPrice(unitPrice);
        li.setTotalPrice(unitPrice.multiply(java.math.BigDecimal.valueOf(quantity)));
        return li;
    }
}

