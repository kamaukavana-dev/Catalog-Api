package com.catalog.product.infrastructure;

import com.catalog.media.domain.ImageStatus;
import com.catalog.product.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    @Query("SELECT i FROM ProductImage i WHERE i.id = :id AND i.deletedAt IS NULL")
    Optional<ProductImage> findActiveById(@Param("id") UUID id);

    @Query("SELECT i FROM ProductImage i WHERE i.product.id = :productId " +
           "AND i.status = 'READY' AND i.deletedAt IS NULL ORDER BY i.sortOrder ASC")
    List<ProductImage> findReadyByProductId(@Param("productId") UUID productId);

    @Query("SELECT i FROM ProductImage i WHERE i.product.id = :productId " +
           "AND i.deletedAt IS NULL ORDER BY i.sortOrder ASC")
    List<ProductImage> findAllByProductId(@Param("productId") UUID productId);

    @Query("SELECT i FROM ProductImage i WHERE i.product.id = :productId " +
           "AND i.primary = TRUE AND i.status = 'READY' AND i.deletedAt IS NULL")
    Optional<ProductImage> findPrimaryByProductId(@Param("productId") UUID productId);

    @Modifying
    @Query("UPDATE ProductImage i SET i.primary = FALSE " +
           "WHERE i.product.id = :productId AND i.primary = TRUE")
    void demoteAllPrimaries(@Param("productId") UUID productId);

    @Query("SELECT i FROM ProductImage i WHERE i.status = 'PENDING' " +
           "AND i.createdAt < :threshold AND i.deletedAt IS NULL")
    List<ProductImage> findStalePending(@Param("threshold") Instant threshold);

    @Query("SELECT COUNT(i) > 0 FROM ProductImage i WHERE i.product.id = :productId " +
           "AND i.status = 'READY' AND i.deletedAt IS NULL")
    boolean hasReadyImages(@Param("productId") UUID productId);

    @Query("SELECT COUNT(i) FROM ProductImage i WHERE i.status = 'PROCESSING' " +
           "AND i.updatedAt < :threshold AND i.deletedAt IS NULL")
    long countStuckInProcessing(@Param("threshold") Instant threshold);

    @Query("SELECT COUNT(i) FROM ProductImage i WHERE i.status = :status " +
           "AND i.deletedAt IS NULL")
    long countByStatus(@Param("status") ImageStatus status);
}
