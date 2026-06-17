package com.catalog.category.infrastructure;

import com.catalog.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Category> findActiveById(@Param("id") UUID id);

    @Query("SELECT c FROM Category c WHERE c.slug = :slug AND c.deletedAt IS NULL")
    Optional<Category> findActiveBySlug(@Param("slug") String slug);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.slug = :slug AND c.deletedAt IS NULL AND c.id <> :excludeId")
    boolean existsBySlugExcluding(@Param("slug") String slug, @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.slug = :slug AND c.deletedAt IS NULL")
    boolean existsBySlug(@Param("slug") String slug);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.deletedAt IS NULL ORDER BY c.displayOrder ASC")
    List<Category> findActiveChildrenByParentId(@Param("parentId") UUID parentId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.parent IS NULL AND c.deletedAt IS NULL ORDER BY c.displayOrder ASC")
    List<Category> findActiveRootCategories();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.deletedAt IS NULL ORDER BY c.depth ASC, c.displayOrder ASC")
    List<Category> findAllActive();

    // pathPrefix is the ancestor path terminated by '/', e.g. "/<rootId>/"; the
    // wildcard is appended here so callers pass a clean prefix and every descendant
    // (child, grandchild, ...) whose materialized path starts with it is returned.
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.path LIKE CONCAT(:pathPrefix, '%') AND c.deletedAt IS NULL ORDER BY c.depth ASC")
    List<Category> findActiveSubtree(@Param("pathPrefix") String pathPrefix);

    @Query("SELECT c FROM Category c WHERE c.id IN :ids AND c.deletedAt IS NULL ORDER BY c.depth ASC")
    List<Category> findAncestorsByIds(@Param("ids") List<UUID> ids);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.parent.id = :parentId AND c.deletedAt IS NULL")
    boolean hasActiveChildren(@Param("parentId") UUID parentId);

    @Modifying
    @Query("UPDATE Category c SET c.path = REPLACE(c.path, :oldPathPrefix, :newPathPrefix) " +
           "WHERE c.path LIKE :likePattern AND c.deletedAt IS NULL")
    int bulkUpdatePath(@Param("oldPathPrefix") String oldPathPrefix,
                       @Param("newPathPrefix") String newPathPrefix,
                       @Param("likePattern") String likePattern);

    @Modifying
    @Query("UPDATE Category c SET c.path = REPLACE(c.path, :oldPathPrefix, :newPathPrefix), " +
           "c.depth = c.depth + :depthDelta " +
           "WHERE c.path LIKE :likePattern AND c.deletedAt IS NULL")
    int bulkUpdatePathAndDepth(@Param("oldPathPrefix") String oldPathPrefix,
                               @Param("newPathPrefix") String newPathPrefix,
                               @Param("likePattern") String likePattern,
                               @Param("depthDelta") int depthDelta);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.id = :categoryId " +
           "AND c.path LIKE :ancestorPattern AND c.deletedAt IS NULL")
    boolean isAncestor(@Param("categoryId") UUID categoryId,
                       @Param("ancestorPattern") String ancestorPattern);
}
