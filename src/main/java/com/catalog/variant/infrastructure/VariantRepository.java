package com.catalog.variant.infrastructure;

import com.catalog.variant.domain.Variant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VariantRepository extends JpaRepository<Variant, UUID> {

    @Query("SELECT v FROM Variant v WHERE v.id = :id AND v.deletedAt IS NULL")
    Optional<Variant> findActiveById(@Param("id") UUID id);

    @Query("SELECT DISTINCT v FROM Variant v " +
            "LEFT JOIN FETCH v.attributeValues av " +
            "LEFT JOIN FETCH av.attributeType " +
            "WHERE v.id = :id AND v.deletedAt IS NULL")
    Optional<Variant> findActiveByIdWithAttributes(@Param("id") UUID id);

    @Query("SELECT DISTINCT v FROM Variant v " +
            "LEFT JOIN FETCH v.attributeValues av " +
            "LEFT JOIN FETCH av.attributeType " +
            "WHERE v.product.id = :productId AND v.deletedAt IS NULL " +
            "ORDER BY v.createdAt ASC")
    List<Variant> findActiveByProductIdWithAttributes(@Param("productId") UUID productId);

    @Query("SELECT v FROM Variant v WHERE v.product.id = :productId " +
            "AND v.deletedAt IS NULL ORDER BY v.createdAt ASC")
    List<Variant> findActiveByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(v) FROM Variant v WHERE v.product.id = :productId " +
            "AND v.status = 'ACTIVE' AND v.deletedAt IS NULL")
    long countActiveByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(v) > 0 FROM Variant v WHERE v.internalSku = :sku")
    boolean existsByInternalSku(@Param("sku") String sku);

    @Query("SELECT COUNT(v) > 0 FROM Variant v WHERE v.merchantSku = :sku AND v.deletedAt IS NULL")
    boolean existsByMerchantSku(@Param("sku") String sku);

    @Query("SELECT COUNT(v) > 0 FROM Variant v WHERE v.merchantSku = :sku " +
            "AND v.deletedAt IS NULL AND v.id <> :excludeId")
    boolean existsByMerchantSkuExcluding(@Param("sku") String sku,
                                         @Param("excludeId") UUID excludeId);

    @Query("SELECT v FROM Variant v WHERE v.internalSku = :sku AND v.deletedAt IS NULL")
    Optional<Variant> findActiveByInternalSku(@Param("sku") String sku);
}
