package com.catalog.product.api;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.GlobalExceptionHandler;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.common.security.IdempotencyFilter;
import com.catalog.common.security.RateLimitingFilter;
import com.catalog.common.security.RedisTokenBucketRateLimiter;
import com.catalog.product.api.dto.request.CreateProductRequest;
import com.catalog.product.api.dto.request.UpdateProductStatusRequest;
import com.catalog.product.api.dto.response.ProductResponse;
import com.catalog.product.application.ProductBulkUpdateService;
import com.catalog.product.application.ProductService;
import com.catalog.product.application.search.CursorPage;
import com.catalog.product.application.search.ProductAdminQueryService;
import com.catalog.product.application.search.ProductCardDto;
import com.catalog.product.application.search.ProductSearchCacheService;
import com.catalog.product.domain.ProductStatus;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProductController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({GlobalExceptionHandler.class, IdempotencyFilter.class, RateLimitingFilter.class})
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ProductService productService;
    @MockitoBean private ProductBulkUpdateService productBulkUpdateService;
    @MockitoBean private ProductSearchCacheService productSearchCacheService;
    @MockitoBean private ProductAdminQueryService productAdminQueryService;

    @MockitoBean private RedisTokenBucketRateLimiter redisTokenBucketRateLimiter;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = (ValueOperations<String, String>) Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        // IdempotencyFilter claims the key atomically via setIfAbsent (SET NX). TRUE means
        // this request won the claim and proceeds; stub it so a first-time request is not
        // treated as an in-flight duplicate (which short-circuits to 409 Idempotency Conflict).
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        when(redisTokenBucketRateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(true);
    }

    @Test
    void shouldReturn201_whenCreatingProductSuccessfully() throws Exception {
        UUID id = UUID.randomUUID();
        ProductResponse response = new ProductResponse(
                id,
                "Test Product",
                "test-product",
                null,
                null,
                ProductStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                Instant.now(),
                Instant.now()
        );
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(new CreateProductRequest(
                "Test Product",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        mockMvc.perform(post("/api/v1/products")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void shouldReturn409_whenCreatingDuplicateResource() throws Exception {
        when(productService.createProduct(any(CreateProductRequest.class)))
                .thenThrow(new DuplicateResourceException("Duplicate SKU: sku-dup"));

        String body = objectMapper.writeValueAsString(new CreateProductRequest(
                "Dup Product",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        mockMvc.perform(post("/api/v1/products")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void shouldReturn422_whenStatusTransitionIsInvalid() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.updateStatus(eq(id), any(UpdateProductStatusRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Illegal product status transition"));

        String body = objectMapper.writeValueAsString(new UpdateProductStatusRequest(ProductStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/products/{id}/status", id)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"));
    }

    @Test
    void shouldReturn200WithEmptyPage_whenSearchingInEmptyCatalog() throws Exception {
        CursorPage<ProductCardDto> empty = CursorPage.of(List.of(), 20, dto -> null);
        when(productSearchCacheService.search(any())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/products/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void shouldReturn404_whenProductNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.getProductById(eq(id)))
                .thenThrow(new ResourceNotFoundException("Product", id));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldNotLeakStackTrace_whenUnhandledExceptionOccurs() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.getProductById(eq(id)))
                .thenThrow(new RuntimeException("boom"));

        String body = mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("at com.");
        assertThat(body).doesNotContain("SELECT");
        assertThat(body).doesNotContain("INSERT");
        assertThat(body).doesNotContain("UPDATE");
        assertThat(body).doesNotContain("DELETE");
    }
}
