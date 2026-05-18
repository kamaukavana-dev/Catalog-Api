package com.catalog.product.application;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.product.domain.BulkProductUpdateJob;
import com.catalog.product.infrastructure.BulkProductUpdateJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class ProductBulkUpdateService {

    private static final int BATCH_SIZE = 50;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel"
    );

    private final BulkProductUpdateJobRepository jobRepository;
    private final BulkProductProcessor batchProcessor;
    private final ObjectMapper objectMapper;
    private final long maxFileSizeBytes;

    public ProductBulkUpdateService(
            BulkProductUpdateJobRepository jobRepository,
            BulkProductProcessor batchProcessor,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${catalog.bulk.product.max-file-size-bytes:10485760}") long maxFileSizeBytes) {
        this.jobRepository = jobRepository;
        this.batchProcessor = batchProcessor;
        this.objectMapper = objectMapper;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional
    public BulkProductUpdateJob submitUpdate(UUID importSessionId, MultipartFile file) {
        validateFile(file);
        return jobRepository.findByImportSessionId(importSessionId)
                .orElseGet(() -> {
                    BulkProductUpdateJob job = BulkProductUpdateJob.create(importSessionId);
                    BulkProductUpdateJob saved = jobRepository.save(job);
                    processAsync(saved.getId(), file);
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public BulkProductUpdateJob getJobStatus(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("BulkProductUpdateJob", jobId));
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
            throw new BusinessRuleViolationException("Invalid file type");
        }
    }

    private boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return ALLOWED_CONTENT_TYPES.contains(normalized);
    }

    @Async
    public void processAsync(UUID jobId, MultipartFile file) {
        markProcessing(jobId);
        try {
            List<ProductRow> rows = parseCSV(file);
            updateTotalRows(jobId, rows.size());

            List<List<ProductRow>> batches = partition(rows, BATCH_SIZE);
            List<RowError> allErrors = new ArrayList<>();
            int processedRows = 0;

            for (int i = 0; i < batches.size(); i++) {
                BatchResult result = batchProcessor.processBatch(batches.get(i), i * BATCH_SIZE);
                processedRows += result.processedCount();
                allErrors.addAll(result.errors());
                updateProgress(jobId, processedRows, allErrors.size());
            }

            completeJob(jobId, processedRows, allErrors);
        } catch (Exception e) {
            failJob(jobId, e.getMessage());
        }
    }

    private List<ProductRow> parseCSV(MultipartFile file) {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader("product_id", "name")
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);

            return StreamSupport.stream(records.spliterator(), false)
                    .map(r -> new ProductRow(UUID.fromString(r.get("product_id")), r.get("name")))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalArgumentException("CSV parse failed: " + e.getMessage());
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(UUID jobId) {
        jobRepository.findById(jobId).ifPresent(j -> { j.markProcessing(); jobRepository.save(j); });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTotalRows(UUID jobId, int total) {
        jobRepository.findById(jobId).ifPresent(j -> { j.setTotalRows(total); jobRepository.save(j); });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID jobId, int processed, int failed) {
        jobRepository.findById(jobId).ifPresent(j -> {
            j.setProcessedRows(processed);
            j.setFailedRows(failed);
            jobRepository.save(j);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeJob(UUID jobId, int processed, List<RowError> errors) {
        jobRepository.findById(jobId).ifPresent(j -> {
            try {
                j.complete(processed, errors.size(), objectMapper.writeValueAsString(errors));
                jobRepository.save(j);
            } catch (Exception e) {
                j.complete(processed, errors.size(), "Error serializing errors");
                jobRepository.save(j);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failJob(UUID jobId, String error) {
        jobRepository.findById(jobId).ifPresent(j -> { j.fail(error); jobRepository.save(j); });
    }

    record ProductRow(UUID productId, String name) {}
    record BatchResult(int processedCount, List<RowError> errors) {}
    record RowError(int row, UUID productId, String error) {}
}
