package com.catalog.product.domain;

import com.catalog.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_bulk_jobs")
@Getter
@Setter
@NoArgsConstructor
public class BulkProductUpdateJob extends BaseEntity {

    @Column(name = "import_session_id", nullable = false, unique = true)
    private UUID importSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BulkJobStatus status;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "error_summary")
    private String errorSummary;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static BulkProductUpdateJob create(UUID sessionId) {
        BulkProductUpdateJob job = new BulkProductUpdateJob();
        job.importSessionId = sessionId;
        job.status = BulkJobStatus.PENDING;
        job.processedRows = 0;
        job.failedRows = 0;
        return job;
    }

    public void markProcessing() {
        this.status = BulkJobStatus.PROCESSING;
    }

    public void complete(int processed, int failed, String errors) {
        this.processedRows = processed;
        this.failedRows = failed;
        this.errorSummary = errors;
        this.completedAt = Instant.now();

        if (failed == 0) {
            this.status = BulkJobStatus.COMPLETED;
        } else if (processed == 0) {
            this.status = BulkJobStatus.FAILED;
        } else {
            this.status = BulkJobStatus.PARTIALLY_FAILED;
        }
    }

    public void fail(String error) {
        this.status = BulkJobStatus.FAILED;
        this.errorSummary = error;
        this.completedAt = Instant.now();
    }

    public enum BulkJobStatus {
        PENDING, PROCESSING, COMPLETED, PARTIALLY_FAILED, FAILED
    }
}
