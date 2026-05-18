package com.catalog.inventory.infrastructure;

import com.catalog.inventory.domain.BulkImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BulkImportJobRepository extends JpaRepository<BulkImportJob, UUID> {
    Optional<BulkImportJob> findByImportSessionId(UUID importSessionId);
}

