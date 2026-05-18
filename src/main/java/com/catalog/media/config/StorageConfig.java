package com.catalog.media.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "catalog.storage")
public class StorageConfig {

    private String bucket;
    private String endpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    private String baseUrl;
    private int presignedUrlExpiryMinutes = 10;
    private long maxFileSizeBytes = 10_485_760L;
    private int pendingCleanupThresholdHours = 24;
    private List<String> allowedContentTypes =
            List.of("image/jpeg", "image/png", "image/webp");

    @Bean
    public S3Client s3Client() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(true)
                .httpClient(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder().build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .endpointOverride(URI.create(endpoint))
                .build();
    }

    public String toPublicUrl(String storageKey) {
        return baseUrl + "/" + storageKey;
    }

    public String buildProductImageKey(java.util.UUID productId, java.util.UUID imageId) {
        return "products/" + productId + "/images/" + imageId;
    }

    public String buildVariantImageKey(java.util.UUID variantId, java.util.UUID imageId) {
        return "variants/" + variantId + "/images/" + imageId;
    }
}
