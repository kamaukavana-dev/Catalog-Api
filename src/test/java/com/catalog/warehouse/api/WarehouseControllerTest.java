package com.catalog.warehouse.api;

import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.GlobalExceptionHandler;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.common.security.IdempotencyFilter;
import com.catalog.common.security.RateLimitingFilter;
import com.catalog.common.security.RedisTokenBucketRateLimiter;
import com.catalog.warehouse.api.dto.request.CreateWarehouseRequest;
import com.catalog.warehouse.api.dto.request.UpdateWarehouseRequest;
import com.catalog.warehouse.api.dto.response.WarehouseResponse;
import com.catalog.warehouse.application.WarehouseService;
import com.catalog.warehouse.domain.WarehouseType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WarehouseController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({GlobalExceptionHandler.class, IdempotencyFilter.class, RateLimitingFilter.class})
class WarehouseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private WarehouseService warehouseService;
    @MockitoBean private RedisTokenBucketRateLimiter redisTokenBucketRateLimiter;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = (ValueOperations<String, String>) Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);

        when(redisTokenBucketRateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(true);
    }

    @Test
    void shouldReturn201_whenCreatingWarehouseSuccessfully() throws Exception {
        WarehouseResponse response = new WarehouseResponse(
                UUID.randomUUID(),
                "WH-1",
                "Main Warehouse",
                WarehouseType.MAIN,
                "Addr",
                "City",
                "US",
                true
        );
        when(warehouseService.createWarehouse(any(CreateWarehouseRequest.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(new CreateWarehouseRequest(
                "wh-1",
                "Main Warehouse",
                WarehouseType.MAIN,
                "Addr",
                "City",
                "US"
        ));

        mockMvc.perform(post("/api/v1/warehouses")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("WH-1"));
    }

    @Test
    void shouldReturn409_whenWarehouseCodeAlreadyExists() throws Exception {
        when(warehouseService.createWarehouse(any(CreateWarehouseRequest.class)))
                .thenThrow(new DuplicateResourceException("Warehouse code 'WH-1' already exists."));

        String body = objectMapper.writeValueAsString(new CreateWarehouseRequest(
                "wh-1",
                "Main Warehouse",
                WarehouseType.MAIN,
                null,
                null,
                null
        ));

        mockMvc.perform(post("/api/v1/warehouses")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturn404_whenPatchingNonExistentWarehouse() throws Exception {
        UUID id = UUID.randomUUID();
        when(warehouseService.updateWarehouse(eq(id), any(UpdateWarehouseRequest.class)))
                .thenThrow(new ResourceNotFoundException("Warehouse", id));

        String body = objectMapper.writeValueAsString(new UpdateWarehouseRequest(
                "New Name",
                null,
                null,
                null,
                null,
                null
        ));

        mockMvc.perform(patch("/api/v1/warehouses/{id}", id)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturn200WithPaginationMetadata_whenListingWarehouses() throws Exception {
        WarehouseResponse w1 = new WarehouseResponse(UUID.randomUUID(), "WH-1", "A", WarehouseType.MAIN, null, null, null, true);
        WarehouseResponse w2 = new WarehouseResponse(UUID.randomUUID(), "WH-2", "B", WarehouseType.MAIN, null, null, null, true);

        when(warehouseService.listWarehouses(any()))
                .thenReturn(new PageImpl<>(List.of(w1, w2), PageRequest.of(0, 50), 2));

        mockMvc.perform(get("/api/v1/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true));
    }
}

