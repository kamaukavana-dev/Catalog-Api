package com.catalog.media.storage;

import java.time.Instant;

public interface StorageService {

    PresignedUploadDetails generatePresignedPut(String storageKey, String contentType);

    StorageObjectMetadata verifyAndGetMetadata(String storageKey);

    java.io.InputStream openStream(String storageKey);

    void delete(String storageKey);

    record PresignedUploadDetails(
        String uploadUrl,
        Instant expiresAt
    ) {}

    record StorageObjectMetadata(
        String contentType,
        long sizeBytes
    ) {}
}

