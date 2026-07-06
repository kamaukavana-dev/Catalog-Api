package com.catalog.product.application;

import com.catalog.product.application.ProductBulkUpdateService.BatchResult;
import com.catalog.product.application.ProductBulkUpdateService.ProductRow;
import com.catalog.product.domain.Product;
import com.catalog.product.event.ProductMutatedEvent;
import com.catalog.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulkProductProcessorTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final BulkProductProcessor processor = new BulkProductProcessor(productRepository, eventPublisher);

    private Product existing(UUID id, String name, String slug) {
        Product p = Product.createDraft(name, slug);
        when(productRepository.findById(id)).thenReturn(Optional.of(p));
        when(productRepository.save(p)).thenReturn(p);
        return p;
    }

    @Test
    void appliesEveryRowAndRenamesAndPublishesEvents() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Product p1 = existing(id1, "Old One", "old-one");
        existing(id2, "Old Two", "old-two");

        BatchResult result = processor.processBatch(
                List.of(new ProductRow(id1, "New One"), new ProductRow(id2, "New Two")), 0);

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        // The rename is applied with a slug derived from the new name.
        assertThat(p1.getName()).isEqualTo("New One");
        assertThat(p1.getSlug()).isEqualTo("new-one");
        verify(productRepository, times(2)).save(any(Product.class));
        verify(eventPublisher, times(2)).publishEvent(any(ProductMutatedEvent.class));
    }

    @Test
    void unknownProductBecomesARowErrorNotACrash() {
        UUID missing = UUID.randomUUID();
        when(productRepository.findById(missing)).thenReturn(Optional.empty());

        BatchResult result = processor.processBatch(List.of(new ProductRow(missing, "Whatever")), 0);

        assertThat(result.processedCount()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).productId()).isEqualTo(missing);
        assertThat(result.errors().get(0).error()).contains("Product not found");
        // absoluteRow = rowOffset(0) + index(0) + 2  (header + 1-based)
        assertThat(result.errors().get(0).row()).isEqualTo(2);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void continuesPastAFailedRowAndReportsOnlyTheBadOne() {
        UUID ok = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        existing(ok, "Good", "good");
        when(productRepository.findById(bad)).thenReturn(Optional.empty());

        BatchResult result = processor.processBatch(
                List.of(new ProductRow(ok, "Good Renamed"), new ProductRow(bad, "Bad")), 0);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).productId()).isEqualTo(bad);
        assertThat(result.errors().get(0).row()).isEqualTo(3); // second row: 0 + 1 + 2
    }

    @Test
    void rowOffsetIsReflectedInReportedRowNumbers() {
        UUID bad = UUID.randomUUID();
        when(productRepository.findById(bad)).thenReturn(Optional.empty());

        BatchResult result = processor.processBatch(List.of(new ProductRow(bad, "X")), 50);

        assertThat(result.errors().get(0).row()).isEqualTo(52); // 50 + 0 + 2
    }

    @Test
    void emptyBatchProcessesNothing() {
        BatchResult result = processor.processBatch(List.of(), 0);

        assertThat(result.processedCount()).isZero();
        assertThat(result.errors()).isEmpty();
        verify(productRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
