package com.catalog.product.application;

import com.catalog.common.util.SlugUtils;
import com.catalog.product.domain.Product;
import com.catalog.product.event.ProductMutatedEvent;
import com.catalog.product.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BulkProductProcessor {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductBulkUpdateService.BatchResult processBatch(List<ProductBulkUpdateService.ProductRow> batch, int rowOffset) {
        int processed = 0;
        List<ProductBulkUpdateService.RowError> errors = new ArrayList<>();

        for (int i = 0; i < batch.size(); i++) {
            ProductBulkUpdateService.ProductRow row = batch.get(i);
            int absoluteRow = rowOffset + i + 2;

            try {
                processRow(row);
                processed++;
            } catch (Exception e) {
                errors.add(new ProductBulkUpdateService.RowError(absoluteRow, row.productId(), e.getMessage()));
                log.warn("Bulk product update row {} failed: id={}: {}", absoluteRow, row.productId(), e.getMessage());
            }
        }

        return new ProductBulkUpdateService.BatchResult(processed, errors);
    }

    private void processRow(ProductBulkUpdateService.ProductRow row) {
        Product product = productRepository.findById(row.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + row.productId()));

        String oldSlug = product.getSlug();
        String newSlug = SlugUtils.toSlug(row.name());

        product.updateName(row.name(), newSlug);
        productRepository.save(product);

        eventPublisher.publishEvent(new ProductMutatedEvent(product.getId(), newSlug, oldSlug, false));
    }
}
