package com.catalog.media.product.application;

import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.media.domain.ImageStatus;
import com.catalog.media.product.api.dto.request.InitiateUploadRequest;
import com.catalog.media.product.api.dto.request.UpdateImageRequest;
import com.catalog.media.product.api.dto.response.ProductImageResponse;
import com.catalog.media.product.api.dto.response.UploadSessionResponse;
import com.catalog.media.storage.StorageService;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductImage;
import com.catalog.product.infrastructure.ProductImageRepository;
import com.catalog.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductImageServiceIT extends BaseIntegrationTest {

    @Autowired
    private ProductImageService imageService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductImageRepository imageRepository;

    @MockitoBean
    private StorageService storageService;
    // Stubbed so confirmUpload does not trigger the real async pipeline in these tests.
    @MockitoBean
    private ImageProcessingService imageProcessingService;

    private Product product;

    @BeforeEach
    void seed() {
        product = productRepository.save(Product.createDraft("Cam", "cam-" + UUID.randomUUID()));
    }

    private ProductImage persist(ImageStatus status) {
        ProductImage img = ProductImage.createPending(
                product, "products/" + product.getId() + "/images/" + UUID.randomUUID(), "alt", "image/png");
        if (status == ImageStatus.PROCESSING) {
            img.markProcessing();
        } else if (status == ImageStatus.READY) {
            img.markProcessing();
            img.markReady("image/png", 100L, 10, 10);
        }
        return imageRepository.save(img);
    }

    @Test
    void initiateUploadPersistsPendingImageAndReturnsPresignedUrl() {
        when(storageService.generatePresignedPut(anyString(), eq("image/png")))
                .thenReturn(new StorageService.PresignedUploadDetails("http://minio/upload", Instant.now().plusSeconds(600)));

        UploadSessionResponse res = imageService.initiateUpload(product.getId(),
                new InitiateUploadRequest("image/png", "front"));

        assertThat(res.uploadUrl()).isEqualTo("http://minio/upload");
        assertThat(res.storageKey()).contains(product.getId().toString());
        ProductImage saved = imageRepository.findActiveById(res.imageId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ImageStatus.PENDING);
        verify(storageService).generatePresignedPut(anyString(), eq("image/png"));
    }

    @Test
    void initiateUploadRejectsUnsupportedContentType() {
        assertThatThrownBy(() -> imageService.initiateUpload(product.getId(),
                new InitiateUploadRequest("application/pdf", "doc")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Unsupported content type");
    }

    @Test
    void initiateUploadThrowsWhenProductMissing() {
        assertThatThrownBy(() -> imageService.initiateUpload(UUID.randomUUID(),
                new InitiateUploadRequest("image/png", "x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmUploadMovesImageToProcessingAndHandsOffToPipeline() {
        ProductImage pending = persist(ImageStatus.PENDING);
        when(storageService.verifyAndGetMetadata(anyString()))
                .thenReturn(new StorageService.StorageObjectMetadata("image/png", 2048L));

        ProductImageResponse res = imageService.confirmUpload(product.getId(), pending.getId());

        assertThat(res.status()).isEqualTo(ImageStatus.PROCESSING);
        assertThat(imageRepository.findActiveById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(ImageStatus.PROCESSING);
        verify(imageProcessingService).processImage(eq(pending.getId()), anyString(), eq("image/png"), anyLong());
    }

    @Test
    void confirmUploadRejectsAndDeletesAnOversizedObject() {
        ProductImage pending = persist(ImageStatus.PENDING);
        when(storageService.verifyAndGetMetadata(anyString()))
                .thenReturn(new StorageService.StorageObjectMetadata("image/png", 50L * 1024 * 1024));

        assertThatThrownBy(() -> imageService.confirmUpload(product.getId(), pending.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("exceeds maximum size");

        verify(storageService).delete(anyString());
        assertThat(imageRepository.findActiveById(pending.getId())).isEmpty();
    }

    @Test
    void confirmUploadRejectsAnObjectWhoseRealContentTypeIsNotAllowed() {
        ProductImage pending = persist(ImageStatus.PENDING);
        when(storageService.verifyAndGetMetadata(anyString()))
                .thenReturn(new StorageService.StorageObjectMetadata("application/x-msdownload", 10L));

        assertThatThrownBy(() -> imageService.confirmUpload(product.getId(), pending.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Unsupported content type");
        verify(storageService).delete(anyString());
    }

    @Test
    void confirmUploadIsRejectedWhenImageIsNotPending() {
        ProductImage processing = persist(ImageStatus.PROCESSING);
        assertThatThrownBy(() -> imageService.confirmUpload(product.getId(), processing.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already confirmed");
    }

    @Test
    void getProductImagesReturnsOnlyReadyImages() {
        persist(ImageStatus.PENDING);
        ProductImage ready = persist(ImageStatus.READY);

        List<ProductImageResponse> images = imageService.getProductImages(product.getId());

        assertThat(images).hasSize(1);
        assertThat(images.get(0).id()).isEqualTo(ready.getId());
        assertThat(images.get(0).status()).isEqualTo(ImageStatus.READY);
    }

    @Test
    void deleteImageRemovesTheObjectAndSoftDeletesTheRecord() {
        ProductImage ready = persist(ImageStatus.READY);

        imageService.deleteImage(product.getId(), ready.getId());

        verify(storageService).delete(anyString());
        assertThat(imageRepository.findActiveById(ready.getId())).isEmpty();
    }

    @Test
    void setAsPrimaryRequiresAReadyImage() {
        ProductImage pending = persist(ImageStatus.PENDING);
        assertThatThrownBy(() -> imageService.setAsPrimary(product.getId(), pending.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("READY");

        ProductImage ready = persist(ImageStatus.READY);
        ProductImageResponse res = imageService.setAsPrimary(product.getId(), ready.getId());
        assertThat(res.isPrimary()).isTrue();
    }

    @Test
    void updateImageChangesAltTextAndSortOrder() {
        ProductImage ready = persist(ImageStatus.READY);

        ProductImageResponse res = imageService.updateImage(product.getId(), ready.getId(),
                new UpdateImageRequest("new alt", 5));

        assertThat(res.altText()).isEqualTo("new alt");
        assertThat(res.sortOrder()).isEqualTo(5);
    }

    @Test
    void operationsOnUnknownImageThrowNotFound() {
        assertThatThrownBy(() -> imageService.deleteImage(product.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void imageBelongingToAnotherProductIsNotFound() {
        ProductImage ready = persist(ImageStatus.READY);
        Product other = productRepository.save(Product.createDraft("Other", "other-" + UUID.randomUUID()));

        assertThatThrownBy(() -> imageService.confirmUpload(other.getId(), ready.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
