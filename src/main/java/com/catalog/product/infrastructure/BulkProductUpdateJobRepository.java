package com.catalog.product.infrastructure;

import com.catalog.product.domain.BulkProductUpdateJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BulkProductUpdateJobRepository extends JpaRepository<BulkProductUpdateJob, UUID> {
    Optional<BulkProductUpdateJob> findByImportSessionId(UUID importSessionId);
}
