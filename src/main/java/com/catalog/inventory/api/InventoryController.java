package com.catalog.inventory.api;

import com.catalog.common.response.ApiResponse;
import com.catalog.common.response.PagedResponse;
import com.catalog.inventory.api.dto.request.AdjustStockRequest;
import com.catalog.inventory.api.dto.request.CreateInventoryRequest;
import com.catalog.inventory.api.dto.request.CreateReservationRequest;
import com.catalog.inventory.api.dto.request.TransferStockRequest;
import com.catalog.inventory.api.dto.response.InventoryJournalResponse;
import com.catalog.inventory.api.dto.response.InventoryResponse;
import com.catalog.inventory.api.dto.response.ReservationResponse;
import com.catalog.inventory.api.dto.response.TransferResponse;
import com.catalog.inventory.application.InventoryService;
import com.catalog.inventory.application.InventoryTransferService;
import com.catalog.inventory.domain.InventoryJournal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryTransferService transferService;

    @PostMapping("/api/v1/inventory")
    public ResponseEntity<ApiResponse<InventoryResponse>> createInventory(@Valid @RequestBody CreateInventoryRequest request) {
        InventoryResponse created = inventoryService.createInventory(request);
        return ResponseEntity.created(URI.create("/api/v1/inventory/" + created.id()))
                .body(ApiResponse.success("Inventory created", created));
    }

    @GetMapping("/api/v1/inventory/{id}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventoryById(id)));
    }

    @GetMapping("/api/v1/variants/{variantId}/inventory")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getInventoryForVariant(@PathVariable UUID variantId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventoryForVariant(variantId)));
    }

    @GetMapping("/api/v1/variants/{variantId}/inventory/warehouses/{warehouseId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(@PathVariable UUID variantId,
                                                                       @PathVariable UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventory(variantId, warehouseId)));
    }

    @PatchMapping("/api/v1/inventory/{id}/stock")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjustStock(@PathVariable UUID id,
                                                                      @Valid @RequestBody AdjustStockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.adjustStock(id, request)));
    }

    @PostMapping("/api/v1/inventory/reservations")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserveStock(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock reserved", inventoryService.reserveStock(request)));
    }

    @PostMapping("/api/v1/inventory/reservations/{id}/complete")
    public ResponseEntity<ApiResponse<ReservationResponse>> completeReservation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Reservation completed", inventoryService.completeReservation(id)));
    }

    @PostMapping("/api/v1/inventory/reservations/{id}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Reservation cancelled", inventoryService.cancelReservation(id)));
    }

    @PostMapping({"/api/v1/inventory/transfers", "/api/v1/transfers"})
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @Valid @RequestBody TransferStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Transfer completed",
                transferService.transfer(request)));
    }

    @GetMapping("/api/v1/inventory/{inventoryId}/journal")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryJournalResponse>>> getJournal(
            @PathVariable UUID inventoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") @Max(200) int size) {
        Page<InventoryJournal> journal = inventoryService.getJournal(inventoryId, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(journal.map(this::toJournalResponse))));
    }

    private InventoryJournalResponse toJournalResponse(InventoryJournal journal) {
        return new InventoryJournalResponse(
                journal.getId(),
                journal.getOperationType().name(),
                journal.getQuantityBefore(),
                journal.getQuantityAfter(),
                journal.getQuantityDelta(),
                journal.getReservedBefore(),
                journal.getReservedAfter(),
                journal.getReservedDelta(),
                journal.getReferenceType(),
                journal.getReferenceId(),
                journal.getActorType().name(),
                journal.getReason(),
                journal.getCreatedAt()
        );
    }
}
