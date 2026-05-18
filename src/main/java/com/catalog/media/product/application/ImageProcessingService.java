package com.catalog.media.product.application;

import com.catalog.product.domain.ProductImage;
import com.catalog.product.infrastructure.ProductImageRepository;
import com.catalog.media.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.UUID;

/**
 * Minimal image processing pipeline.
 *
 * Current behavior: verifies object exists, records metadata, and marks READY.
 * Real deployments should extend this to generate thumbnails and validate image integrity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProcessingService {

    private final ProductImageRepository imageRepository;
    private final StorageService storageService;

    @Async
    @Transactional
    public void processImage(UUID imageId, String storageKey, String contentType, long sizeBytes) {
        ProductImage image = imageRepository.findActiveById(imageId).orElse(null);
        if (image == null) {
            log.warn("Image processing aborted: image not found id={}", imageId);
            return;
        }

        try {
            storageService.verifyAndGetMetadata(storageKey);
            int width = 0;
            int height = 0;
            try (var stream = storageService.openStream(storageKey)) {
                BufferedImage buffered = ImageIO.read(stream);
                if (buffered == null) {
                    throw new IllegalArgumentException("Uploaded file is not a valid image.");
                }
                width = buffered.getWidth();
                height = buffered.getHeight();
            }
            image.markReady(contentType, sizeBytes, width, height);
            imageRepository.save(image);
            log.info("Image processing complete: imageId={} key={}", imageId, storageKey);
        } catch (Exception ex) {
            image.markFailed();
            imageRepository.save(image);
            log.error("Image processing failed: imageId={} error={}", imageId, ex.getMessage());
        }
    }
}
