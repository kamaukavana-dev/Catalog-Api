package com.catalog.product.api;

import com.catalog.common.response.ApiResponse;
import com.catalog.common.response.PagedResponse;
import com.catalog.product.api.dto.request.CreateProductRequest;
import com.catalog.product.api.dto.request.UpdateProductRequest;
import com.catalog.product.api.dto.request.UpdateProductStatusRequest;
import com.catalog.product.api.dto.response.ProductResponse;
import com.catalog.product.api.dto.response.ProductSummaryResponse;
import com.catalog.product.application.ProductBulkUpdateService;
import com.catalog.product.application.ProductService;
import com.catalog.product.application.search.AdminProductFilterParams;
import com.catalog.product.application.search.CursorPage;
import com.catalog.product.application.search.ProductAdminQueryService;
import com.catalog.product.application.search.ProductCardDto;
import com.catalog.product.application.search.ProductFilterParams;
import com.catalog.product.application.search.ProductSearchCacheService;
import com.catalog.product.application.search.SortOption;
import com.catalog.product.domain.BulkProductUpdateJob;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ProductService productService;
    private final ProductBulkUpdateService productBulkUpdateService;
    private final ProductSearchCacheService productSearchCacheService;
    private final ProductAdminQueryService productAdminQueryService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", productService.createProduct(request)));
    }

    @PostMapping("/bulk-update")
    public ResponseEntity<ApiResponse<BulkProductUpdateJob>> bulkUpdateProducts(
            @RequestParam("importSessionId") UUID importSessionId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                "Product bulk update started.",
                productBulkUpdateService.submitUpdate(importSessionId, file)));
    }

    @GetMapping("/bulk-update/{jobId}")
    public ResponseEntity<ApiResponse<BulkProductUpdateJob>> getBulkUpdateStatus(
            @PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(productBulkUpdateService.getJobStatus(jobId)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<CursorPage<ProductCardDto>>> searchProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false) java.util.Set<UUID> attributeValueIds,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "NEWEST") SortOption sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int pageSize) {

        ProductFilterParams params = new ProductFilterParams(
                categoryId, brandId, minPrice, maxPrice,
                attributeValueIds, inStock, search, sort, cursor, pageSize
        );

        return ResponseEntity.ok(ApiResponse.success(productSearchCacheService.search(params)));
    }

    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<PagedResponse<ProductSummaryResponse>>> adminListProducts(
            @RequestParam(required = false) java.util.Set<ProductStatus> statuses,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") @jakarta.validation.constraints.Max(200) int size,
            @RequestParam(defaultValue = "NEWEST") SortOption sort) {

        AdminProductFilterParams params = new AdminProductFilterParams(statuses, categoryId, brandId, search, sort, page, size);
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Page<Product> results = productAdminQueryService.adminList(params, PageRequest.of(page, safeSize));
        Page<ProductSummaryResponse> mapped = results.map(productService::toSummaryResponse);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(mapped)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.success(productService.getProductById(id)));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlug(
            @PathVariable String slug) {

        return ResponseEntity.ok(ApiResponse.success(productService.getProductBySlug(slug)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductStatusRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Product status updated", productService.updateStatus(id, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Product updated", productService.updateProduct(id, request)));
    }

    @PostMapping("/{id}/categories/{categoryId}")
    public ResponseEntity<ApiResponse<ProductResponse>> addSecondaryCategory(
            @PathVariable UUID id,
            @PathVariable UUID categoryId) {

        return ResponseEntity.ok(ApiResponse.success(productService.addSecondaryCategory(id, categoryId)));
    }

    @DeleteMapping("/{id}/categories/{categoryId}")
    public ResponseEntity<ApiResponse<ProductResponse>> removeSecondaryCategory(
            @PathVariable UUID id,
            @PathVariable UUID categoryId) {

        return ResponseEntity.ok(ApiResponse.success(productService.removeSecondaryCategory(id, categoryId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable UUID id) {

        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }
}
