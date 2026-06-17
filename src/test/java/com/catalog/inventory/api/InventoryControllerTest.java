package com.catalog.inventory.api;

import com.catalog.common.exception.GlobalExceptionHandler;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.common.security.IdempotencyFilter;
import com.catalog.common.security.RateLimitingFilter;
import com.catalog.common.security.RedisTokenBucketRateLimiter;
import com.catalog.inventory.api.dto.request.CreateInventoryRequest;
import com.catalog.inventory.api.dto.request.TransferStockRequest;
import com.catalog.inventory.api.dto.response.InventoryResponse;
import com.catalog.inventory.api.dto.response.TransferResponse;
import com.catalog.inventory.application.InventoryService;
import com.catalog.inventory.application.InventoryTransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InventoryController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({GlobalExceptionHandler.class, IdempotencyFilter.class, RateLimitingFilter.class})
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private InventoryService inventoryService;
    @MockitoBean private InventoryTransferService transferService;

    @MockitoBean private RedisTokenBucketRateLimiter redisTokenBucketRateLimiter;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // IdempotencyFilter: allow processing (no cached response).
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = (ValueOperations<String, String>) Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);

        // RateLimitingFilter: allow by default.
        when(redisTokenBucketRateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(true);
    }

    @Test
    void shouldReturn201WithLocationHeader_whenCreatingInventoryWithValidBody() throws Exception {
        UUID id = UUID.randomUUID();
        InventoryResponse response = new InventoryResponse(
                id,
                UUID.randomUUID(),
                "SKU-1",
                UUID.randomUUID(),
                "WH-1",
                "Warehouse 1",
                10,
                0,
                10,
                0,
                false,
                false,
                Instant.now()
        );
        when(inventoryService.createInventory(any(CreateInventoryRequest.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(new CreateInventoryRequest(
                response.variantId(),
                response.warehouseId(),
                10,
                0
        ));

        mockMvc.perform(post("/api/v1/inventory")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/inventory/" + id))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void shouldReturn422WithFieldErrors_whenRequiredFieldMissing() throws Exception {
        // variantId missing (null) triggers bean validation
        String body = """
                {"warehouseId":"%s","initialQuantity":0,"reorderLevel":0}
                """.formatted(UUID.randomUUID());

        String responseBody = mockMvc.perform(post("/api/v1/inventory")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(jsonPath("$.validationErrors[0].field").exists())
                .andExpect(jsonPath("$.validationErrors[0].message").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody).doesNotContain("Exception");
        assertThat(responseBody).doesNotContain("at com.");
        assertThat(responseBody).doesNotContain("SELECT");
        assertThat(responseBody).doesNotContain("INSERT");
        assertThat(responseBody).doesNotContain("UPDATE");
        assertThat(responseBody).doesNotContain("DELETE");
    }

    @Test
    void shouldReturn404WithStructuredEnvelope_whenInventoryDoesNotExist() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(inventoryService.getInventoryById(eq(missingId)))
                .thenThrow(new ResourceNotFoundException("Inventory", missingId));

        String responseBody = mockMvc.perform(get("/api/v1/inventory/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/inventory/" + missingId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody).doesNotContain("Exception");
        assertThat(responseBody).doesNotContain("at com.");
    }

    @Test
    void shouldReturn201Created_whenSubmittingTransfer() throws Exception {
        TransferResponse transferResponse = new TransferResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10,
                UUID.randomUUID(),
                10,
                5
        );
        when(transferService.transfer(any(TransferStockRequest.class))).thenReturn(transferResponse);

        String body = objectMapper.writeValueAsString(new TransferStockRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                5,
                "rebalance"
        ));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quantityTransferred").value(5));
    }

    @Test
    void shouldReturn400_whenIdempotencyKeyHeaderMissingOnMutation() throws Exception {
        UUID variantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        String body = objectMapper.writeValueAsString(new CreateInventoryRequest(
                variantId, warehouseId, 1, 0
        ));

        mockMvc.perform(post("/api/v1/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("X-Idempotency-Key")));
    }

    @Test
    void shouldReturn429WithRetryAfter_whenRateLimited() throws Exception {
        when(redisTokenBucketRateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(false);

        String body = objectMapper.writeValueAsString(new CreateInventoryRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                0
        ));

        mockMvc.perform(post("/api/v1/inventory")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }
}
