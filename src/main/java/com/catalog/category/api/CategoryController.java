package com.catalog.category.api;

import com.catalog.category.api.dto.request.CreateCategoryRequest;
import com.catalog.category.api.dto.request.UpdateCategoryRequest;
import com.catalog.category.api.dto.response.CategoryResponse;
import com.catalog.category.api.dto.response.CategorySummaryResponse;
import com.catalog.category.api.dto.response.CategoryTreeResponse;
import com.catalog.category.application.CategoryService;
import com.catalog.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {

        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                ApiResponse.success(categoryService.getCategoryById(id)));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryBySlug(
            @PathVariable String slug) {

        return ResponseEntity.ok(
                ApiResponse.success(categoryService.getCategoryBySlug(slug)));
    }

    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getCategoryTree() {
        return ResponseEntity.ok(
                ApiResponse.success(categoryService.getCategoryTree()));
    }

    @GetMapping("/{id}/subtree")
    public ResponseEntity<ApiResponse<CategoryTreeResponse>> getSubtree(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                ApiResponse.success(categoryService.getSubtree(id)));
    }

    @GetMapping("/{id}/children")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getDirectChildren(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                ApiResponse.success(categoryService.getDirectChildren(id)));
    }

    @GetMapping("/{id}/ancestors")
    public ResponseEntity<ApiResponse<List<CategorySummaryResponse>>> getAncestors(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                ApiResponse.success(categoryService.getAncestors(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Category updated successfully",
                        categoryService.updateCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable UUID id) {

        categoryService.deleteCategory(id);
        return ResponseEntity.ok(
                ApiResponse.success("Category deleted successfully"));
    }
}

