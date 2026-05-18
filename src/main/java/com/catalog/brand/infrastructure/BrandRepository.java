package com.catalog.brand.infrastructure;

import com.catalog.brand.domain.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {

    @Query("SELECT b FROM Brand b WHERE b.id = :id AND b.deletedAt IS NULL")
    Optional<Brand> findActiveById(@Param("id") UUID id);

    @Query("SELECT b FROM Brand b WHERE b.slug = :slug AND b.deletedAt IS NULL")
    Optional<Brand> findActiveBySlug(@Param("slug") String slug);

    @Query("SELECT COUNT(b) > 0 FROM Brand b " +
           "WHERE b.slug = :slug AND b.deletedAt IS NULL AND b.id <> :excludeId")
    boolean existsBySlugExcluding(@Param("slug") String slug,
                                   @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(b) > 0 FROM Brand b WHERE b.slug = :slug AND b.deletedAt IS NULL")
    boolean existsBySlug(@Param("slug") String slug);

    @Query("SELECT COUNT(b) > 0 FROM Brand b " +
           "WHERE LOWER(b.name) = LOWER(:name) AND b.deletedAt IS NULL AND b.id <> :excludeId")
    boolean existsByNameExcluding(@Param("name") String name,
                                   @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(b) > 0 FROM Brand b " +
           "WHERE LOWER(b.name) = LOWER(:name) AND b.deletedAt IS NULL")
    boolean existsByName(@Param("name") String name);

    @Query("""
            SELECT b FROM Brand b
            WHERE b.deletedAt IS NULL
              AND (:search IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:active IS NULL OR b.active = :active)
              AND (:featured IS NULL OR b.featured = :featured)
              AND (:country IS NULL OR b.countryOfOrigin = :country)
            """)
    Page<Brand> findByFilters(
            @Param("search") String search,
            @Param("active") Boolean active,
            @Param("featured") Boolean featured,
            @Param("country") String country,
            Pageable pageable);

    @Query("SELECT b FROM Brand b WHERE b.featured = true AND b.active = true " +
           "AND b.deletedAt IS NULL ORDER BY b.name ASC")
    List<Brand> findFeaturedActiveBrands();

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.brand.id = :brandId AND p.deletedAt IS NULL")
    boolean hasActiveProducts(@Param("brandId") UUID brandId);
}

