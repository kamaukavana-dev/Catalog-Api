package com.catalog.attribute.infrastructure;

import com.catalog.attribute.domain.AttributeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttributeTypeRepository extends JpaRepository<AttributeType, UUID> {

    @Query("SELECT a FROM AttributeType a WHERE a.deletedAt IS NULL ORDER BY a.displayOrder ASC")
    List<AttributeType> findAllActive();

    @Query("SELECT a FROM AttributeType a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<AttributeType> findActiveById(@Param("id") UUID id);

    @Query("SELECT COUNT(a) > 0 FROM AttributeType a WHERE LOWER(a.name) = LOWER(:name) AND a.deletedAt IS NULL")
    boolean existsByName(@Param("name") String name);
}

