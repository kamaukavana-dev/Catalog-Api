package com.catalog.common.config;

import com.catalog.inventory.infrastructure.InventoryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterBinder lowStockGauge(InventoryRepository inventoryRepository) {
        return registry -> Gauge.builder("catalog.inventory.low_stock_variants",
                inventoryRepository, repo -> {
                    try {
                        return repo.countLowStock();
                    } catch (Exception e) {
                        return -1; // -1 indicates query failure
                    }
                })
                .description("Number of variant-warehouse combinations at or below reorder level. " +
                             "Operational replenishment signal.")
                .register(registry);
    }
}
