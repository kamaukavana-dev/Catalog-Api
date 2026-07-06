package com.catalog.media.product.application;

import com.catalog.media.domain.ImageStatus;
import com.catalog.media.storage.StorageService;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductImage;
import com.catalog.product.infrastructure.ProductImageRepository;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageProcessingServiceTest {

    private final ProductImageRepository imageRepository = mock(ProductImageRepository.class);
    private final StorageService storageService = mock(StorageService.class);
    private final ImageProcessingService service = new ImageProcessingService(imageRepository, storageService);

    private ProductImage pendingImage() {
        ProductImage image = ProductImage.createPending(
                Product.createDraft("P", "p-" + UUID.randomUUID()), "products/x/images/y", "alt", "image/png");
        image.markProcessing();
        return image;
    }

    private static byte[] tinyPng() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void marksImageReadyWhenStorageReturnsAValidImage() throws Exception {
        UUID imageId = UUID.randomUUID();
        ProductImage image = pendingImage();
        when(imageRepository.findActiveById(imageId)).thenReturn(Optional.of(image));
        when(storageService.verifyAndGetMetadata(anyString()))
                .thenReturn(new StorageService.StorageObjectMetadata("image/png", 100L));
        when(storageService.openStream(anyString())).thenReturn(new ByteArrayInputStream(tinyPng()));

        service.processImage(imageId, "products/x/images/y", "image/png", 100L);

        assertThat(image.getStatus()).isEqualTo(ImageStatus.READY);
        assertThat(image.getWidthPx()).isEqualTo(2);
        assertThat(image.getHeightPx()).isEqualTo(2);
        verify(imageRepository).save(image);
    }

    @Test
    void marksImageFailedWhenStorageIsUnreachable() {
        UUID imageId = UUID.randomUUID();
        ProductImage image = pendingImage();
        when(imageRepository.findActiveById(imageId)).thenReturn(Optional.of(image));
        when(storageService.verifyAndGetMetadata(anyString()))
                .thenThrow(new RuntimeException("storage unreachable"));

        service.processImage(imageId, "products/x/images/y", "image/png", 100L);

        assertThat(image.getStatus()).isEqualTo(ImageStatus.FAILED);
        verify(imageRepository).save(image);
    }

    @Test
    void marksImageFailedWhenTheObjectIsNotADecodableImage() {
        UUID imageId = UUID.randomUUID();
        ProductImage image = pendingImage();
        when(imageRepository.findActiveById(imageId)).thenReturn(Optional.of(image));
        when(storageService.verifyAndGetMetadata(anyString()))
                .thenReturn(new StorageService.StorageObjectMetadata("image/png", 3L));
        when(storageService.openStream(anyString()))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3})); // not an image

        service.processImage(imageId, "products/x/images/y", "image/png", 3L);

        assertThat(image.getStatus()).isEqualTo(ImageStatus.FAILED);
        verify(imageRepository).save(image);
    }

    @Test
    void isANoOpWhenTheImageRecordIsGone() {
        UUID imageId = UUID.randomUUID();
        when(imageRepository.findActiveById(imageId)).thenReturn(Optional.empty());

        service.processImage(imageId, "k", "image/png", 1L);

        verify(imageRepository, never()).save(any());
    }
}
