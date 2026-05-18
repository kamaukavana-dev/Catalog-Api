package com.catalog.brand.api;

import com.catalog.brand.api.dto.request.CreateBrandRequest;
import com.catalog.brand.api.dto.request.UpdateBrandRequest;
import com.catalog.brand.api.dto.response.BrandResponse;
import com.catalog.brand.api.dto.response.BrandSummaryResponse;
import com.catalog.brand.application.BrandService;
import com.catalog.common.response.ApiResponse;
import com.catalog.common.response.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final BrandService brandService;

    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(
            @Valid @RequestBody CreateBrandRequest request) {

        BrandResponse response = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Brand created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<BrandResponse>>> getBrands(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, safeSize, sort);
        PagedResponse<BrandResponse> response =
                brandService.getBrands(search, active, featured, country, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<BrandSummaryResponse>>> getFeaturedBrands() {
        return ResponseEntity.ok(ApiResponse.success(brandService.getFeaturedBrands()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.success(brandService.getBrandById(id)));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandBySlug(
            @PathVariable String slug) {

        return ResponseEntity.ok(ApiResponse.success(brandService.getBrandBySlug(slug)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBrandRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Brand updated successfully",
                brandService.updateBrand(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(
            @PathVariable UUID id) {

        brandService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.success("Brand deleted successfully"));
    }
}

