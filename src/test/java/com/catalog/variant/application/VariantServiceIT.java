package com.catalog.variant.application;

import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.product.domain.Product;
import com.catalog.product.infrastructure.ProductRepository;
import com.catalog.variant.api.dto.request.CreateVariantRequest;
import com.catalog.variant.api.dto.request.UpdateVariantRequest;
import com.catalog.variant.api.dto.request.UpdateVariantStatusRequest;
import com.catalog.variant.api.dto.response.VariantResponse;
import com.catalog.variant.domain.TaxClass;
import com.catalog.variant.domain.Variant;
import com.catalog.variant.domain.VariantStatus;
import com.catalog.variant.infrastructure.VariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavior-asserting integration tests for {@link VariantService} backed by a real
 * Testcontainers PostgreSQL database. Extends {@link BaseIntegrationTest}, which
 * truncates all tables before each test.
 */
public class VariantServiceIT extends BaseIntegrationTest {

    @Autowired
    private VariantService variantService;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private ProductRepository productRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        product = productRepository.save(
                Product.createDraft("Test Product", "test-product-" + UUID.randomUUID()));
    }

    /**
     * Build a create request. attributeValueIds is passed as an empty set so the
     * service's attribute resolution is a no-op (valid) and no attribute fixtures
     * are required.
     */
    private CreateVariantRequest createRequest(String merchantSku,
                                               BigDecimal basePrice,
                                               TaxClass taxClass) {
        return new CreateVariantRequest(
                merchantSku,
                basePrice,
                null,   // salePrice
                null,   // saleStartAt
                null,   // saleEndAt
                null,   // costPrice
                taxClass,
                Set.of(),
                null,   // weightGrams
                null,   // lengthMm
                null,   // widthMm
                null    // heightMm
        );
    }

    @Test
    void shouldCreateVariantWithGeneratedSkuAndReturnPopulatedResponse() {
        VariantResponse response = variantService.createVariant(
                product.getId(),
                createRequest("MER-001", new BigDecimal("49.99"), TaxClass.STANDARD));

        assertThat(response.id()).isNotNull();
        assertThat(response.productId()).isEqualTo(product.getId());
        assertThat(response.internalSku()).isNotBlank();
        assertThat(response.merchantSku()).isEqualTo("MER-001");
        assertThat(response.status()).isEqualTo(VariantStatus.DRAFT);
        assertThat(response.basePrice()).isEqualByComparingTo("49.99");
        assertThat(response.taxClass()).isEqualTo(TaxClass.STANDARD);
        // No active sale -> effective price equals base price.
        assertThat(response.effectivePrice()).isEqualByComparingTo("49.99");
        assertThat(response.saleActive()).isFalse();

        Variant persisted = variantRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.getMerchantSku()).isEqualTo("MER-001");
        assertThat(persisted.getStatus()).isEqualTo(VariantStatus.DRAFT);
    }

    @Test
    void shouldGetVariantByIdUnderProduct() {
        VariantResponse created = variantService.createVariant(
                product.getId(),
                createRequest(null, new BigDecimal("10.00"), TaxClass.STANDARD));

        VariantResponse fetched = variantService.getVariantById(product.getId(), created.id());

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.basePrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldUpdateVariantPricingAndTaxClass() {
        VariantResponse created = variantService.createVariant(
                product.getId(),
                createRequest("MER-UPD", new BigDecimal("20.00"), TaxClass.STANDARD));

        UpdateVariantRequest update = new UpdateVariantRequest(
                "MER-UPD-2",
                new BigDecimal("30.00"),
                null,
                null,
                null,
                new BigDecimal("15.00"),
                TaxClass.REDUCED,
                null,
                null,
                null,
                null,
                null
        );

        VariantResponse updated = variantService.updateVariant(product.getId(), created.id(), update);

        assertThat(updated.merchantSku()).isEqualTo("MER-UPD-2");
        assertThat(updated.basePrice()).isEqualByComparingTo("30.00");
        assertThat(updated.costPrice()).isEqualByComparingTo("15.00");
        assertThat(updated.taxClass()).isEqualTo(TaxClass.REDUCED);

        Variant persisted = variantRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getBasePrice()).isEqualByComparingTo("30.00");
        assertThat(persisted.getTaxClass()).isEqualTo(TaxClass.REDUCED);
    }

    @Test
    void shouldTransitionVariantDraftToActive() {
        VariantResponse created = variantService.createVariant(
                product.getId(),
                createRequest(null, new BigDecimal("12.00"), TaxClass.STANDARD));

        VariantResponse result = variantService.updateStatus(
                product.getId(), created.id(), new UpdateVariantStatusRequest(VariantStatus.ACTIVE));

        assertThat(result.status()).isEqualTo(VariantStatus.ACTIVE);

        Variant persisted = variantRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(VariantStatus.ACTIVE);
    }

    @Test
    void shouldRejectIllegalVariantStatusTransition() {
        VariantResponse created = variantService.createVariant(
                product.getId(),
                createRequest(null, new BigDecimal("12.00"), TaxClass.STANDARD));

        // DRAFT allows ACTIVE or ARCHIVED, but not DISCONTINUED.
        assertThatThrownBy(() -> variantService.updateStatus(
                product.getId(), created.id(),
                new UpdateVariantStatusRequest(VariantStatus.DISCONTINUED)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Illegal variant status transition");
    }

    @Test
    void shouldDeleteDraftVariant() {
        VariantResponse created = variantService.createVariant(
                product.getId(),
                createRequest(null, new BigDecimal("8.00"), TaxClass.STANDARD));

        variantService.deleteVariant(product.getId(), created.id());

        Variant persisted = variantRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.isDeleted()).isTrue();

        assertThatThrownBy(() -> variantService.getVariantById(product.getId(), created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldRejectDeletingActiveVariant() {
        VariantResponse created = variantService.createVariant(
                product.getId(),
                createRequest(null, new BigDecimal("8.00"), TaxClass.STANDARD));
        variantService.updateStatus(product.getId(), created.id(),
                new UpdateVariantStatusRequest(VariantStatus.ACTIVE));

        assertThatThrownBy(() -> variantService.deleteVariant(product.getId(), created.id()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cannot delete an ACTIVE variant");
    }

    @Test
    void shouldRejectDuplicateMerchantSkuOnCreate() {
        variantService.createVariant(
                product.getId(),
                createRequest("SHARED-SKU", new BigDecimal("10.00"), TaxClass.STANDARD));

        // Merchant-SKU uniqueness is global, but attribute-combination uniqueness is
        // per-product. Both variants here carry an empty attribute set, so reusing the same
        // product would trip the (earlier) attribute-combo check instead of the SKU check.
        // Put the duplicate SKU on a second product to isolate the merchant-SKU rejection.
        Product other = productRepository.save(
                Product.createDraft("Other Product", "other-product-" + UUID.randomUUID()));

        assertThatThrownBy(() -> variantService.createVariant(
                other.getId(),
                createRequest("SHARED-SKU", new BigDecimal("11.00"), TaxClass.STANDARD)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("SHARED-SKU");
    }

    @Test
    void shouldRejectNegativeBasePriceOnCreate() {
        assertThatThrownBy(() -> variantService.createVariant(
                product.getId(),
                createRequest(null, new BigDecimal("-1.00"), TaxClass.STANDARD)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Base price must be greater than zero");
    }

    @Test
    void shouldRejectZeroBasePriceOnCreate() {
        assertThatThrownBy(() -> variantService.createVariant(
                product.getId(),
                createRequest(null, BigDecimal.ZERO, TaxClass.STANDARD)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Base price must be greater than zero");
    }

    @Test
    void shouldRejectNullTaxClassOnCreate() {
        assertThatThrownBy(() -> variantService.createVariant(
                product.getId(),
                createRequest(null, new BigDecimal("10.00"), null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Tax class is required");
    }

    @Test
    void shouldRejectSalePriceWithoutTimeBounds() {
        CreateVariantRequest request = new CreateVariantRequest(
                null,
                new BigDecimal("20.00"),
                new BigDecimal("15.00"),   // salePrice present
                null,                       // no saleStartAt
                null,                       // no saleEndAt
                null,
                TaxClass.STANDARD,
                Set.of(),
                null, null, null, null
        );

        assertThatThrownBy(() -> variantService.createVariant(product.getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("at least one time bound");
    }

    @Test
    void shouldThrowResourceNotFoundWhenGettingUnknownVariant() {
        UUID unknownVariant = UUID.randomUUID();

        assertThatThrownBy(() -> variantService.getVariantById(product.getId(), unknownVariant))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Variant");
    }

    @Test
    void shouldThrowResourceNotFoundWhenCreatingVariantForUnknownProduct() {
        UUID unknownProduct = UUID.randomUUID();

        assertThatThrownBy(() -> variantService.createVariant(
                unknownProduct,
                createRequest(null, new BigDecimal("10.00"), TaxClass.STANDARD)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
    }
}
