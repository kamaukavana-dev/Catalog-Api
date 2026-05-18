package com.catalog.attribute.infrastructure;

import com.catalog.attribute.domain.AttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AttributeValueRepository extends JpaRepository<AttributeValue, UUID> {

    @Query("SELECT av FROM AttributeValue av WHERE av.attributeType.id = :typeId AND av.deletedAt IS NULL ORDER BY av.displayOrder ASC")
    List<AttributeValue> findActiveByTypeId(@Param("typeId") UUID typeId);

    @Query("SELECT av FROM AttributeValue av WHERE av.id = :id AND av.deletedAt IS NULL")
    Optional<AttributeValue> findActiveById(@Param("id") UUID id);

    @Query("SELECT av FROM AttributeValue av WHERE av.id IN :ids AND av.deletedAt IS NULL")
    Set<AttributeValue> findActiveByIds(@Param("ids") Set<UUID> ids);

    @Query("SELECT DISTINCT av.attributeType.id FROM AttributeValue av WHERE av.id IN :ids AND av.deletedAt IS NULL")
    Set<UUID> findDistinctTypeIdsByValueIds(@Param("ids") Set<UUID> ids);

    @Query("SELECT COUNT(av) > 0 FROM AttributeValue av " +
            "WHERE av.attributeType.id = :typeId " +
            "AND LOWER(av.value) = LOWER(:value) " +
            "AND av.deletedAt IS NULL")
    boolean existsByTypeAndValue(@Param("typeId") UUID typeId, @Param("value") String value);

    @Query("SELECT COUNT(v) > 0 FROM Variant v JOIN v.attributeValues av " +
            "WHERE av.id = :valueId AND v.deletedAt IS NULL")
    boolean isUsedByVariants(@Param("valueId") UUID valueId);
}
