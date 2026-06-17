package com.catalog.warehouse.infrastructure;

import com.catalog.warehouse.domain.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    @Query("SELECT w FROM Warehouse w WHERE w.id = :id AND w.deletedAt IS NULL")
    Optional<Warehouse> findActiveById(@Param("id") UUID id);

    @Query("SELECT w FROM Warehouse w WHERE w.code = :code AND w.deletedAt IS NULL")
    Optional<Warehouse> findActiveByCode(@Param("code") String code);

    @Query("SELECT COUNT(w) > 0 FROM Warehouse w WHERE UPPER(w.code) = UPPER(:code) AND w.deletedAt IS NULL")
    boolean existsByCode(@Param("code") String code);

    @Query("SELECT w FROM Warehouse w WHERE w.deletedAt IS NULL ORDER BY w.code ASC")
    List<Warehouse> findAllActive();

    @Query(
            value = "SELECT w FROM Warehouse w WHERE w.deletedAt IS NULL",
            countQuery = "SELECT COUNT(w) FROM Warehouse w WHERE w.deletedAt IS NULL"
    )
    Page<Warehouse> findPageActive(Pageable pageable);
}
