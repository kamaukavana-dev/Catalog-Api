package com.catalog.product.application;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.product.domain.BulkProductUpdateJob;
import com.catalog.product.infrastructure.BulkProductUpdateJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductBulkUpdateServiceTest {

    private ProductBulkUpdateService bulkUpdateService;

    @Mock
    private BulkProductUpdateJobRepository jobRepository;

    @Mock
    private BulkProductProcessor batchProcessor;

    @Mock
    private ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        bulkUpdateService = new ProductBulkUpdateService(jobRepository, batchProcessor, objectMapper, 1024 * 1024);
    }

    @Test
    void submitUpdate_whenNewSession_createsJob() {
        // Given
        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "id,name\n1,test".getBytes());
        
        when(jobRepository.findByImportSessionId(sessionId)).thenReturn(Optional.empty());
        when(jobRepository.save(any(BulkProductUpdateJob.class))).thenAnswer(i -> i.getArgument(0));

        // When
        BulkProductUpdateJob job = bulkUpdateService.submitUpdate(sessionId, file);

        // Then
        assertNotNull(job);
        assertEquals(sessionId, job.getImportSessionId());
        verify(jobRepository).save(any(BulkProductUpdateJob.class));
    }

    @Test
    void submitUpdate_whenExistingSession_returnsExistingJob() {
        // Given
        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "id,name\n1,test".getBytes());
        BulkProductUpdateJob existingJob = BulkProductUpdateJob.create(sessionId);
        
        when(jobRepository.findByImportSessionId(sessionId)).thenReturn(Optional.of(existingJob));

        // When
        BulkProductUpdateJob job = bulkUpdateService.submitUpdate(sessionId, file);

        // Then
        assertEquals(existingJob, job);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void submitUpdate_whenInvalidFile_throwsException() {
        // Given
        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());

        // When & Then
        assertThrows(BusinessRuleViolationException.class, () -> bulkUpdateService.submitUpdate(sessionId, file));
    }

    @Test
    void submitUpdate_whenCsvHasCharset_acceptsFile() {
        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv; charset=utf-8",
                ("product_id,name\n" + UUID.randomUUID() + ",test").getBytes());

        when(jobRepository.findByImportSessionId(sessionId)).thenReturn(Optional.empty());
        when(jobRepository.save(any(BulkProductUpdateJob.class))).thenAnswer(i -> i.getArgument(0));

        BulkProductUpdateJob job = bulkUpdateService.submitUpdate(sessionId, file);

        assertNotNull(job);
        verify(jobRepository).save(any(BulkProductUpdateJob.class));
    }

    @Test
    void submitUpdate_whenFileEmpty_throwsAndNeverPersists() {
        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                () -> bulkUpdateService.submitUpdate(sessionId, file));
        assertTrue(ex.getMessage().contains("empty"), ex.getMessage());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void submitUpdate_whenFileExceedsMaxSize_throws() {
        UUID sessionId = UUID.randomUUID();
        // Service was constructed with a 1 MiB limit; one byte over trips the size guard.
        byte[] tooBig = new byte[(1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.csv", "text/csv", tooBig);

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                () -> bulkUpdateService.submitUpdate(sessionId, file));
        assertTrue(ex.getMessage().contains("maximum"), ex.getMessage());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void submitUpdate_whenContentTypeNull_throwsInvalidType() {
        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "mystery.bin", null, "product_id,name".getBytes());

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                () -> bulkUpdateService.submitUpdate(sessionId, file));
        assertTrue(ex.getMessage().contains("Invalid file type"), ex.getMessage());
    }

    @Test
    void getJobStatus_whenPresent_returnsJob() {
        UUID jobId = UUID.randomUUID();
        BulkProductUpdateJob existing = BulkProductUpdateJob.create(UUID.randomUUID());
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(existing));

        assertEquals(existing, bulkUpdateService.getJobStatus(jobId));
    }

    @Test
    void getJobStatus_whenMissing_throwsResourceNotFound() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bulkUpdateService.getJobStatus(jobId));
    }
}
