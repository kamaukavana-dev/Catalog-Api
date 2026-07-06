package com.catalog.product.application;

import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.product.api.dto.request.CreateProductRequest;
import com.catalog.product.api.dto.request.UpdateProductRequest;
import com.catalog.product.api.dto.request.UpdateProductStatusRequest;
import com.catalog.product.api.dto.response.ProductResponse;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductStatus;
import com.catalog.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavior-asserting integration tests for {@link ProductService} backed by a real
 * Testcontainers PostgreSQL database. Extends {@link BaseIntegrationTest}, which
 * truncates all tables before each test.
 */
public class ProductServiceIT extends BaseIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    private CreateProductRequest createRequest(String name, String slug, UUID categoryId, UUID brandId) {
        return new CreateProductRequest(
                name,
                slug,
                "short desc",
                "long description",
                categoryId,
                brandId,
                "meta title",
                "meta description"
        );
    }

    private Category persistCategory() {
        return categoryRepository.save(
                Category.createRoot("Root", "root-" + UUID.randomUUID(), "desc"));
    }

    private Brand persistBrand() {
        return brandRepository.save(
                Brand.create("Acme", "acme-" + UUID.randomUUID(), "desc"));
    }

    @Test
    void shouldCreateProductWithCategoryAndBrandAndReturnPopulatedResponse() {
        Category category = persistCategory();
        Brand brand = persistBrand();

        ProductResponse response = productService.createProduct(
                createRequest("iPhone 15", "iphone-15", category.getId(), brand.getId()));

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("iPhone 15");
        assertThat(response.slug()).isEqualTo("iphone-15");
        assertThat(response.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(response.shortDescription()).isEqualTo("short desc");
        assertThat(response.primaryCategoryId()).isEqualTo(category.getId());
        assertThat(response.brandId()).isEqualTo(brand.getId());

        Product persisted = productRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.getSlug()).isEqualTo("iphone-15");
        assertThat(persisted.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    void shouldDeriveSlugFromNameWhenSlugBlank() {
        ProductResponse response = productService.createProduct(
                createRequest("Cool Gadget Pro", null, null, null));

        assertThat(response.slug()).isEqualTo("cool-gadget-pro");
    }

    @Test
    void shouldGetProductById() {
        ProductResponse created = productService.createProduct(
                createRequest("Widget", "widget", null, null));

        ProductResponse fetched = productService.getProductById(created.id());

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.name()).isEqualTo("Widget");
        assertThat(fetched.slug()).isEqualTo("widget");
    }

    @Test
    void shouldGetProductBySlug() {
        productService.createProduct(createRequest("Widget", "widget-slug", null, null));

        ProductResponse fetched = productService.getProductBySlug("widget-slug");

        assertThat(fetched.slug()).isEqualTo("widget-slug");
        assertThat(fetched.name()).isEqualTo("Widget");
    }

    @Test
    void shouldUpdateProductNameAndContent() {
        ProductResponse created = productService.createProduct(
                createRequest("Old Name", "old-name", null, null));

        UpdateProductRequest update = new UpdateProductRequest(
                "New Name",
                "new-name",
                "new short",
                "new description",
                null,
                null,
                "new meta title",
                "new meta description"
        );

        ProductResponse updated = productService.updateProduct(created.id(), update);

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.slug()).isEqualTo("new-name");
        assertThat(updated.shortDescription()).isEqualTo("new short");
        assertThat(updated.metaTitle()).isEqualTo("new meta title");

        Product persisted = productRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("New Name");
        assertThat(persisted.getSlug()).isEqualTo("new-name");
    }

    @Test
    void shouldTransitionDraftToArchived() {
        ProductResponse created = productService.createProduct(
                createRequest("Archivable", "archivable", null, null));

        ProductResponse result = productService.updateStatus(
                created.id(), new UpdateProductStatusRequest(ProductStatus.ARCHIVED));

        assertThat(result.status()).isEqualTo(ProductStatus.ARCHIVED);

        Product persisted = productRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
    }

    @Test
    void shouldRejectActivationWhenNoActiveVariantExists() {
        ProductResponse created = productService.createProduct(
                createRequest("Needs Variant", "needs-variant", null, null));

        assertThatThrownBy(() -> productService.updateStatus(
                created.id(), new UpdateProductStatusRequest(ProductStatus.ACTIVE)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("at least one ACTIVE variant is required");
    }

    @Test
    void shouldRejectIllegalStatusTransitionFromArchived() {
        ProductResponse created = productService.createProduct(
                createRequest("Frozen", "frozen", null, null));
        productService.updateStatus(created.id(), new UpdateProductStatusRequest(ProductStatus.ARCHIVED));

        // ARCHIVED has no allowed transitions -> ACTIVE is illegal
        assertThatThrownBy(() -> productService.updateStatus(
                created.id(), new UpdateProductStatusRequest(ProductStatus.ACTIVE)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Illegal product status transition");
    }

    @Test
    void shouldRejectModifyingArchivedProduct() {
        ProductResponse created = productService.createProduct(
                createRequest("ToArchive", "to-archive", null, null));
        productService.updateStatus(created.id(), new UpdateProductStatusRequest(ProductStatus.ARCHIVED));

        UpdateProductRequest update = new UpdateProductRequest(
                "Renamed", "renamed", null, null, null, null, null, null);

        assertThatThrownBy(() -> productService.updateProduct(created.id(), update))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("cannot be modified");
    }

    @Test
    void shouldAddSecondaryCategory() {
        ProductResponse created = productService.createProduct(
                createRequest("Linkable", "linkable", null, null));
        Category secondary = persistCategory();

        ProductResponse result = productService.addSecondaryCategory(created.id(), secondary.getId());

        assertThat(result.secondaryCategoryIds()).contains(secondary.getId());
    }

    @Test
    void shouldRemoveSecondaryCategory() {
        ProductResponse created = productService.createProduct(
                createRequest("Unlinkable", "unlinkable", null, null));
        Category secondary = persistCategory();
        productService.addSecondaryCategory(created.id(), secondary.getId());

        ProductResponse result = productService.removeSecondaryCategory(created.id(), secondary.getId());

        assertThat(result.secondaryCategoryIds()).doesNotContain(secondary.getId());
    }

    @Test
    void shouldRejectAddingSecondaryCategoryEqualToPrimary() {
        Category category = persistCategory();
        ProductResponse created = productService.createProduct(
                createRequest("Conflict", "conflict", category.getId(), null));

        assertThatThrownBy(() -> productService.addSecondaryCategory(created.id(), category.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("primary category");
    }

    @Test
    void shouldSoftDeleteDraftProduct() {
        ProductResponse created = productService.createProduct(
                createRequest("Deletable", "deletable", null, null));

        productService.deleteProduct(created.id());

        // Soft-deleted: row still present but deletedAt set, and no longer active-findable.
        Product persisted = productRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.isDeleted()).isTrue();

        assertThatThrownBy(() -> productService.getProductById(created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldRejectDuplicateSlugOnCreate() {
        productService.createProduct(createRequest("First", "dup-slug", null, null));

        assertThatThrownBy(() -> productService.createProduct(
                createRequest("Second", "dup-slug", null, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("dup-slug");
    }

    @Test
    void shouldThrowResourceNotFoundWhenGettingUnknownProduct() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> productService.getProductById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void shouldThrowResourceNotFoundWhenCreatingWithUnknownCategory() {
        UUID unknownCategory = UUID.randomUUID();

        assertThatThrownBy(() -> productService.createProduct(
                createRequest("BadCat", "bad-cat", unknownCategory, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }
}
