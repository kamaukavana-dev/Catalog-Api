package com.catalog.media.storage;

import com.catalog.media.config.StorageConfig;
import com.catalog.common.exception.StorageUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final StorageConfig config;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Override
    public PresignedUploadDetails generatePresignedPut(String storageKey, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(config.getBucket())
                .key(storageKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(config.getPresignedUrlExpiryMinutes()))
                .putObjectRequest(putObjectRequest)
                .build();

        var presigned = s3Presigner.presignPutObject(presignRequest);
        Instant expiresAt = presigned.expiration();

        log.debug("Generated presigned PUT for key={} expires={}", storageKey, expiresAt);
        return new PresignedUploadDetails(presigned.url().toString(), expiresAt);
    }

    @Override
    @CircuitBreaker(name = "object-storage", fallbackMethod = "verifyFallback")
    @Retry(name = "object-storage")
    public StorageObjectMetadata verifyAndGetMetadata(String storageKey) {
        try {
            HeadObjectResponse head = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(storageKey)
                            .build());

            return new StorageObjectMetadata(head.contentType(), head.contentLength());

        } catch (NoSuchKeyException e) {
            throw new com.catalog.common.exception.StorageObjectNotFoundException(
                "Upload not found in storage. Key: " + storageKey +
                ". Ensure the upload completed successfully before confirming.");
        } catch (SdkClientException e) {
            log.error("Storage connectivity error verifying key={}: {}", storageKey, e.getMessage());
            throw new com.catalog.common.exception.StorageUnavailableException("Storage service is unreachable.", e);
        }
    }

    // Fallback: if storage circuit is OPEN, fail fast with clear error
    private StorageObjectMetadata verifyFallback(String storageKey, Exception ex) {
        throw new StorageUnavailableException(
            "Object storage is temporarily unavailable. Please retry in 60 seconds.", ex);
    }

    @Override
    public InputStream openStream(String storageKey) {
        return s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(config.getBucket())
                        .key(storageKey)
                        .build());
    }

    @Override
    @CircuitBreaker(name = "object-storage")
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(storageKey)
                            .build());
            log.info("Deleted storage object: key={}", storageKey);
        } catch (Exception e) {
            log.warn("Failed to delete storage object key={}: {}", storageKey, e.getMessage());
        }
    }
}
