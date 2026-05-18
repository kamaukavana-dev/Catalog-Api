package com.catalog.variant.api;

import com.catalog.common.response.ApiResponse;
import com.catalog.variant.api.dto.request.CreateVariantRequest;
import com.catalog.variant.api.dto.request.UpdateVariantRequest;
import com.catalog.variant.api.dto.request.UpdateVariantStatusRequest;
import com.catalog.variant.api.dto.response.VariantResponse;
import com.catalog.variant.api.dto.response.VariantSummaryResponse;
import com.catalog.variant.application.VariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/variants")
@RequiredArgsConstructor
public class VariantController {

    private final VariantService variantService;

    @PostMapping
    public ResponseEntity<ApiResponse<VariantResponse>> createVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody CreateVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Variant created successfully",
                        variantService.createVariant(productId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<VariantSummaryResponse>>> getVariants(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.success(
                variantService.getVariantsForProduct(productId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VariantResponse>> getVariantById(
            @PathVariable UUID productId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                variantService.getVariantById(productId, id)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<VariantResponse>> updateStatus(
            @PathVariable UUID productId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVariantStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Variant status updated",
                variantService.updateStatus(productId, id, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VariantResponse>> updateVariant(
            @PathVariable UUID productId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVariantRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Variant updated",
                variantService.updateVariant(productId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable UUID productId,
            @PathVariable UUID id) {
        variantService.deleteVariant(productId, id);
        return ResponseEntity.ok(ApiResponse.success("Variant deleted"));
    }
}

