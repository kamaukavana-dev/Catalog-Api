package com.catalog.inventory.api;

import com.catalog.common.response.ApiResponse;
import com.catalog.inventory.api.dto.response.BulkImportJobResponse;
import com.catalog.inventory.application.BulkInventoryService;
import com.catalog.inventory.domain.BulkImportJob;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/bulk-imports")
@RequiredArgsConstructor
public class BulkImportController {

    private final BulkInventoryService bulkService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BulkImportJobResponse>> submitImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("importSessionId") UUID importSessionId) {

        BulkImportJob job = bulkService.submitImport(importSessionId, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Import job submitted", toResponse(job)));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<BulkImportJobResponse>> getJobStatus(
            @PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(bulkService.getJobStatus(jobId))));
    }

    private BulkImportJobResponse toResponse(BulkImportJob job) {
        return new BulkImportJobResponse(
                job.getId(),
                job.getImportSessionId(),
                job.getStatus(),
                job.getTotalRows(),
                job.getProcessedRows(),
                job.getFailedRows(),
                job.getErrorSummary(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }
}

