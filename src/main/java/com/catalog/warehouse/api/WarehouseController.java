package com.catalog.warehouse.api;

import com.catalog.common.response.ApiResponse;
import com.catalog.common.response.PagedResponse;
import com.catalog.warehouse.api.dto.request.CreateWarehouseRequest;
import com.catalog.warehouse.api.dto.request.UpdateWarehouseRequest;
import com.catalog.warehouse.api.dto.response.WarehouseResponse;
import com.catalog.warehouse.application.WarehouseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private static final int MAX_PAGE_SIZE = 200;

    private final WarehouseService warehouseService;

    @PostMapping
    public ResponseEntity<ApiResponse<WarehouseResponse>> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Warehouse created successfully", warehouseService.createWarehouse(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<WarehouseResponse>>> getWarehouses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") @Max(200) int size) {

        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Page<WarehouseResponse> results = warehouseService.listWarehouses(PageRequest.of(page, safeSize));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(results)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getWarehouseById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouseById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateWarehouse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Warehouse updated successfully", warehouseService.updateWarehouse(id, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> patchWarehouse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Warehouse updated successfully", warehouseService.updateWarehouse(id, request)));
    }
}
