package com.catalog.product.domain;

import com.catalog.common.audit.BaseEntity;
import com.catalog.media.domain.ImageStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    // S3/MinIO object key — the ONLY persistent storage reference.
    // Public URL = config.storage.base-url + "/" + storageKey
    @Column(name = "storage_key", length = 500, updatable = false)
    private String storageKey;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImageStatus status;

    public static ProductImage createPending(Product product, String storageKey,
                                              String altText, String contentType) {
        ProductImage image = new ProductImage();
        image.product = product;
        image.storageKey = storageKey;
        image.altText = altText;
        image.contentType = contentType;
        image.status = ImageStatus.PENDING;
        image.primary = false;
        image.sortOrder = 0;
        return image;
    }

    public void markProcessing() {
        if (this.status != ImageStatus.PENDING) {
            throw new com.catalog.common.exception.BusinessRuleViolationException(
                "Only PENDING images can be moved to PROCESSING. Current status: " + this.status);
        }
        this.status = ImageStatus.PROCESSING;
    }

    public void markReady(String contentType, long fileSizeBytes,
                           int widthPx, int heightPx) {
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.status = ImageStatus.READY;
    }

    public void markFailed() {
        this.status = ImageStatus.FAILED;
    }
}

