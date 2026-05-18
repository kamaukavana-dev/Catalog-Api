package com.catalog.product.infrastructure;

import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findActiveById(@Param("id") UUID id);

    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN FETCH p.brand
            LEFT JOIN FETCH p.primaryCategory
            LEFT JOIN FETCH p.secondaryCategories
            WHERE p.id = :id AND p.deletedAt IS NULL
            """)
    Optional<Product> findActiveByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN FETCH p.brand
            LEFT JOIN FETCH p.primaryCategory
            LEFT JOIN FETCH p.secondaryCategories
            WHERE p.slug = :slug AND p.deletedAt IS NULL
            """)
    Optional<Product> findActiveBySlugWithDetails(@Param("slug") String slug);

    @Query(value = """
            SELECT p FROM Product p
            LEFT JOIN FETCH p.brand
            LEFT JOIN FETCH p.primaryCategory
            WHERE p.deletedAt IS NULL
              AND (:status IS NULL OR p.status = :status)
              AND (:brandId IS NULL OR p.brand.id = :brandId)
              AND (:primaryCategoryId IS NULL OR p.primaryCategory.id = :primaryCategoryId)
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.deletedAt IS NULL
              AND (:status IS NULL OR p.status = :status)
              AND (:brandId IS NULL OR p.brand.id = :brandId)
              AND (:primaryCategoryId IS NULL OR p.primaryCategory.id = :primaryCategoryId)
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Product> findByFilters(
            @Param("status") ProductStatus status,
            @Param("brandId") UUID brandId,
            @Param("primaryCategoryId") UUID primaryCategoryId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.slug = :slug AND p.deletedAt IS NULL")
    boolean existsBySlug(@Param("slug") String slug);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.slug = :slug AND p.deletedAt IS NULL AND p.id <> :excludeId")
    boolean existsBySlugExcluding(@Param("slug") String slug, @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.brand.id = :brandId AND p.deletedAt IS NULL")
    long countActiveByBrandId(@Param("brandId") UUID brandId);

    @Query("""
            SELECT COUNT(p) FROM Product p
            WHERE p.deletedAt IS NULL
              AND (p.primaryCategory.id = :categoryId
                   OR EXISTS (SELECT 1 FROM p.secondaryCategories sc WHERE sc.id = :categoryId))
             """)
    long countActiveByCategoryId(@Param("categoryId") UUID categoryId);

    /**
     * Returns pg_class.reltuples estimate for fast approximate counts.
     * Accurate within ~5-10% after regular VACUUM/ANALYZE.
     * Never use this for financial reconciliation — estimates only.
     */
    @Query(value = "SELECT reltuples::bigint FROM pg_class WHERE relname = 'products'",
           nativeQuery = true)
    Long estimateProductCount();
}
