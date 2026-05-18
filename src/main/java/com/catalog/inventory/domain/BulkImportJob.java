package com.catalog.inventory.domain;

import com.catalog.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bulk_import_jobs")
@Getter
@Setter
@NoArgsConstructor
public class BulkImportJob extends BaseEntity {

    @Column(name = "import_session_id", nullable = false, unique = true)
    private UUID importSessionId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "submitted_by", length = 200)
    private String submittedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static BulkImportJob create(UUID importSessionId) {
        BulkImportJob job = new BulkImportJob();
        job.importSessionId = importSessionId;
        job.type = "INVENTORY_ADJUSTMENT";
        job.status = "PENDING";
        job.processedRows = 0;
        job.failedRows = 0;
        return job;
    }

    public void markProcessing() {
        this.status = "PROCESSING";
    }

    public void complete(int processed, int failed, String errors) {
        this.processedRows = processed;
        this.failedRows = failed;
        this.errorSummary = errors;
        this.completedAt = Instant.now();
        if (failed > 0) {
            this.status = "PARTIALLY_FAILED";
        } else {
            this.status = "COMPLETED";
        }
    }

    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.errorSummary = errorMessage;
        this.completedAt = Instant.now();
    }
}

