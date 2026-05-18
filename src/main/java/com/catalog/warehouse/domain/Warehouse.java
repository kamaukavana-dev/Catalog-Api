package com.catalog.warehouse.domain;

import com.catalog.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
public class Warehouse extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private WarehouseType type;

    @Column(name = "address_line1", length = 300)
    private String addressLine1;

    @Column(name = "city", length = 100)
    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public static Warehouse create(String code, String name, WarehouseType type) {
        Warehouse w = new Warehouse();
        w.code = code.trim().toUpperCase();
        w.name = name.trim();
        w.type = type;
        w.active = true;
        return w;
    }
}

