package com.catalog.media.product.application;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.media.config.StorageConfig;
import com.catalog.media.domain.ImageStatus;
import com.catalog.media.product.api.dto.request.InitiateUploadRequest;
import com.catalog.media.product.api.dto.request.UpdateImageRequest;
import com.catalog.media.product.api.dto.response.ProductImageResponse;
import com.catalog.media.product.api.dto.response.UploadSessionResponse;
import com.catalog.media.storage.StorageService;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductImage;
import com.catalog.product.domain.ProductStatus;
import com.catalog.product.infrastructure.ProductImageRepository;
import com.catalog.product.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final StorageService storageService;
    private final ImageProcessingService imageProcessingService;
    private final StorageConfig storageConfig;

    @Transactional
    public UploadSessionResponse initiateUpload(UUID productId, InitiateUploadRequest request) {
        Product product = findActiveProductOrThrow(productId);

        if (!storageConfig.getAllowedContentTypes().contains(request.contentType())) {
            throw new BusinessRuleViolationException(
                "Unsupported content type: " + request.contentType() +
                ". Allowed: " + storageConfig.getAllowedContentTypes());
        }

        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new BusinessRuleViolationException(
                "Cannot add images to archived product '" + product.getName() + "'.");
        }

        UUID imageId = UUID.randomUUID();
        String storageKey = storageConfig.buildProductImageKey(productId, imageId);

        StorageService.PresignedUploadDetails presigned =
                storageService.generatePresignedPut(storageKey, request.contentType());

        ProductImage image = ProductImage.createPending(
                product, storageKey, request.altText(), request.contentType());

        image.setId(imageId);
        ProductImage saved = imageRepository.save(image);

        log.info("Upload session created: productId={} imageId={} key={}",
                productId, saved.getId(), storageKey);

        return new UploadSessionResponse(
                saved.getId(),
                presigned.uploadUrl(),
                presigned.expiresAt(),
                storageKey
        );
    }

    @Transactional
    public ProductImageResponse confirmUpload(UUID productId, UUID imageId) {
        ProductImage image = findImageOrThrow(imageId);
        assertBelongsToProduct(image, productId);

        if (image.getStatus() != ImageStatus.PENDING) {
            throw new BusinessRuleViolationException(
                "Upload already confirmed. Current status: " + image.getStatus());
        }

        StorageService.StorageObjectMetadata metadata =
                storageService.verifyAndGetMetadata(image.getStorageKey());

        if (metadata.sizeBytes() > storageConfig.getMaxFileSizeBytes()) {
            storageService.delete(image.getStorageKey());
            image.markDeleted();
            imageRepository.save(image);

            throw new BusinessRuleViolationException(
                String.format("Uploaded file exceeds maximum size of %d bytes. Actual size: %d bytes.",
                        storageConfig.getMaxFileSizeBytes(), metadata.sizeBytes()));
        }

        if (!storageConfig.getAllowedContentTypes().contains(metadata.contentType())) {
            storageService.delete(image.getStorageKey());
            image.markDeleted();
            imageRepository.save(image);

            throw new BusinessRuleViolationException(
                "Unsupported content type: " + metadata.contentType() +
                ". Allowed: " + storageConfig.getAllowedContentTypes());
        }

        image.markProcessing();
        ProductImage saved = imageRepository.save(image);

        imageProcessingService.processImage(saved.getId(), saved.getStorageKey(),
                metadata.contentType(), metadata.sizeBytes());

        log.info("Upload confirmed: imageId={} key={} size={}",
                imageId, image.getStorageKey(), metadata.sizeBytes());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getProductImages(UUID productId) {
        findActiveProductOrThrow(productId);
        return imageRepository.findReadyByProductId(productId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductImageResponse setAsPrimary(UUID productId, UUID imageId) {
        ProductImage image = findImageOrThrow(imageId);
        assertBelongsToProduct(image, productId);

        if (image.getStatus() != ImageStatus.READY) {
            throw new BusinessRuleViolationException(
                "Only READY images can be set as primary. Current status: " + image.getStatus());
        }

        imageRepository.demoteAllPrimaries(productId);

        image.setPrimary(true);
        ProductImage saved = imageRepository.save(image);

        log.info("Primary image set: productId={} imageId={}", productId, imageId);
        return toResponse(saved);
    }

    @Transactional
    public ProductImageResponse updateImage(UUID productId, UUID imageId,
                                             UpdateImageRequest request) {
        ProductImage image = findImageOrThrow(imageId);
        assertBelongsToProduct(image, productId);

        if (request.altText() != null) image.setAltText(request.altText());
        if (request.sortOrder() != null) image.setSortOrder(request.sortOrder());

        return toResponse(imageRepository.save(image));
    }

    @Transactional
    public void deleteImage(UUID productId, UUID imageId) {
        ProductImage image = findImageOrThrow(imageId);
        assertBelongsToProduct(image, productId);

        storageService.delete(image.getStorageKey());

        image.markDeleted();
        imageRepository.save(image);

        log.info("Deleted image: productId={} imageId={}", productId, imageId);
    }

    private Product findActiveProductOrThrow(UUID productId) {
        return productRepository.findActiveById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private ProductImage findImageOrThrow(UUID imageId) {
        return imageRepository.findActiveById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", imageId));
    }

    private void assertBelongsToProduct(ProductImage image, UUID productId) {
        if (!image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("ProductImage", image.getId());
        }
    }

    private ProductImageResponse toResponse(ProductImage image) {
        return new ProductImageResponse(
            image.getId(),
            image.getStorageKey() != null
                ? storageConfig.toPublicUrl(image.getStorageKey())
                : null,
            image.getAltText(),
            image.isPrimary(),
            image.getSortOrder(),
            image.getStatus(),
            image.getContentType(),
            image.getFileSizeBytes(),
            image.getWidthPx(),
            image.getHeightPx(),
            image.getCreatedAt()
        );
    }
}
