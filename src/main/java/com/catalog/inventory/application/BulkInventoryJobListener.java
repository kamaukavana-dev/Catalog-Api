package com.catalog.inventory.application;

import com.catalog.common.web.ByteArrayMultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;

/**
 * Async listener that runs bulk inventory jobs off-thread.
 */
@Component
@RequiredArgsConstructor
public class BulkInventoryJobListener {

    private final BulkInventoryService bulkInventoryService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(BulkInventoryService.BulkInventoryJobSubmitted event) {
        MultipartFile file = new ByteArrayMultipartFile(
                event.name(),
                event.originalFilename(),
                event.contentType(),
                event.bytes()
        );
        bulkInventoryService.processAsync(event.jobId(), file);
    }
}
