package com.catalog.inventory.application;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.inventory.domain.*;
import com.catalog.inventory.infrastructure.*;
import com.catalog.variant.infrastructure.VariantRepository;
import com.catalog.warehouse.infrastructure.WarehouseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class BulkInventoryService {

    private static final int BATCH_SIZE = 100;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel"
    );

    private final InventoryRepository inventoryRepository;
    private final InventoryJournalRepository journalRepository;
    private final VariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final BulkImportJobRepository importJobRepository;
    private final BulkInventoryProcessor batchProcessor;
    private final ObjectMapper objectMapper;
    private final long maxFileSizeBytes;
    private final ApplicationEventPublisher eventPublisher;
    private final long startDelayMs;

    public BulkInventoryService(
            InventoryRepository inventoryRepository,
            InventoryJournalRepository journalRepository,
            VariantRepository variantRepository,
            WarehouseRepository warehouseRepository,
            BulkImportJobRepository importJobRepository,
            BulkInventoryProcessor batchProcessor,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${catalog.bulk.inventory.max-file-size-bytes:10485760}") long maxFileSizeBytes,
            ApplicationEventPublisher eventPublisher,
            @org.springframework.beans.factory.annotation.Value("${catalog.bulk.inventory.start-delay-ms:0}") long startDelayMs) {
        this.inventoryRepository = inventoryRepository;
        this.journalRepository = journalRepository;
        this.variantRepository = variantRepository;
        this.warehouseRepository = warehouseRepository;
        this.importJobRepository = importJobRepository;
        this.batchProcessor = batchProcessor;
        this.objectMapper = objectMapper;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.eventPublisher = eventPublisher;
        this.startDelayMs = startDelayMs;
    }

    /**
     * Idempotent job submission.
     * Same import_session_id = return existing job (no reprocessing).
     */
    @Transactional
    public BulkImportJob submitImport(UUID importSessionId, MultipartFile file) {
        validateFile(file);
        BufferedUpload buffered = bufferFile(file);
        // Idempotency check: same session ID = same job
        return importJobRepository.findByImportSessionId(importSessionId)
                .orElseGet(() -> {
                    BulkImportJob job = BulkImportJob.create(importSessionId);
                    BulkImportJob saved = importJobRepository.save(job);
                    // Kick off async processing
                    eventPublisher.publishEvent(new BulkInventoryJobSubmitted(
                            saved.getId(),
                            buffered.name(),
                            buffered.originalFilename(),
                            buffered.contentType(),
                            buffered.bytes()
                    ));
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public BulkImportJob getJobStatus(UUID jobId) {
        return importJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("BulkImportJob", jobId));
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessRuleViolationException("File is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessRuleViolationException("File exceeds maximum allowed size");
        }
        String contentType = file.getContentType();
        if (!isAllowedContentType(contentType)) {
            throw new BusinessRuleViolationException("Invalid file type. Only CSV files are allowed.");
        }
    }

    private boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return ALLOWED_CONTENT_TYPES.contains(normalized);
    }

    /**
     * Async bulk processor.
     *
     * Runs on the general async thread pool. Does NOT hold a transaction —
     * each batch method opens its own transaction via processBatch().
     * This is intentional: we never hold a long-lived transaction across all batches.
     */
    @Async
    public void processAsync(UUID jobId, MultipartFile file) {
        log.info("Bulk import starting: jobId={}", jobId);
        markProcessing(jobId);
        if (startDelayMs > 0) {
            try {
                Thread.sleep(startDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            List<AdjustmentRow> rows = parseCSV(file);
            updateTotalRows(jobId, rows.size());

            // Partition into fixed-size batches
            List<List<AdjustmentRow>> batches = partition(rows, BATCH_SIZE);

            List<RowError> allErrors = new ArrayList<>();
            int processedRows = 0;

            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                List<AdjustmentRow> batch = batches.get(batchIndex);
                BatchResult result = batchProcessor.processBatch(batch, batchIndex * BATCH_SIZE);
                processedRows += result.processedCount();
                allErrors.addAll(result.errors());

                updateProgress(jobId, processedRows, allErrors.size());
                log.debug("Bulk import progress: jobId={} batch={}/{} errors={}",
                        jobId, batchIndex + 1, batches.size(), allErrors.size());

                // Fail-fast: once an error occurs, stop processing further batches.
                if (!result.errors().isEmpty()) {
                    // Mark all remaining rows as failed without applying them.
                    int remainingStart = (batchIndex + 1) * BATCH_SIZE;
                    for (int r = remainingStart; r < rows.size(); r++) {
                        AdjustmentRow skipped = rows.get(r);
                        int absoluteRow = r + 2; // + header row
                        allErrors.add(new RowError(
                                absoluteRow,
                                skipped.variantSku(),
                                skipped.warehouseCode(),
                                "Skipped due to previous row failure"
                        ));
                    }
                    updateProgress(jobId, processedRows, allErrors.size());
                    break;
                }
            }

            completeJob(jobId, processedRows, allErrors);

        } catch (Exception e) {
            log.error("Bulk import failed fatally: jobId={}: {}", jobId, e.getMessage(), e);
            failJob(jobId, e.getMessage());
        }
    }

    private BufferedUpload bufferFile(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return new BufferedUpload(
                    file.getName() == null ? "file" : file.getName(),
                    file.getOriginalFilename() == null ? "import.csv" : file.getOriginalFilename(),
                    file.getContentType(),
                    bytes
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read uploaded file bytes: " + e.getMessage(), e);
        }
    }

    record BufferedUpload(String name, String originalFilename, String contentType, byte[] bytes) {}

    public record BulkInventoryJobSubmitted(UUID jobId, String name, String originalFilename,
                                           String contentType, byte[] bytes) {}

    private List<AdjustmentRow> parseCSV(MultipartFile file) {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .builder()
                    .setHeader("variant_sku", "warehouse_code",
                               "adjustment_type", "quantity", "reason")
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);

            return StreamSupport.stream(records.spliterator(), false)
                    .map(r -> new AdjustmentRow(
                        r.get("variant_sku"),
                        r.get("warehouse_code"),
                        r.get("adjustment_type"),
                        Integer.parseInt(r.get("quantity")),
                        r.get("reason")
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV: " + e.getMessage(), e);
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    // Job state management — each uses a separate @Transactional call
    // to immediately persist progress without waiting for the batch loop

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(UUID jobId) {
        importJobRepository.findById(jobId).ifPresent(job -> {
            job.markProcessing();
            importJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTotalRows(UUID jobId, int total) {
        importJobRepository.findById(jobId).ifPresent(job -> {
            job.setTotalRows(total);
            importJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID jobId, int processed, int failed) {
        importJobRepository.findById(jobId).ifPresent(job -> {
            job.setProcessedRows(processed);
            job.setFailedRows(failed);
            importJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeJob(UUID jobId, int processed, List<RowError> errors) {
        importJobRepository.findById(jobId).ifPresent(job -> {
            job.complete(processed, errors.size(), serializeErrors(errors));
            importJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failJob(UUID jobId, String errorMessage) {
        importJobRepository.findById(jobId).ifPresent(job -> {
            job.fail(errorMessage);
            importJobRepository.save(job);
        });
    }

    private String serializeErrors(List<RowError> errors) {
        if (errors.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (Exception e) {
            return "[serialization error]";
        }
    }

    // Internal records
    record AdjustmentRow(String variantSku, String warehouseCode,
                          String adjustmentType, int quantity, String reason) {}
    record BatchResult(int processedCount, List<RowError> errors) {}
    record RowError(int row, String variantSku, String warehouseCode, String error) {}
}
