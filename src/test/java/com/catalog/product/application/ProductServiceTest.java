package com.catalog.product.application;

import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.product.api.dto.request.CreateProductRequest;
import com.catalog.product.api.dto.request.UpdateProductStatusRequest;
import com.catalog.product.api.mapper.ProductMapper;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductStatus;
import com.catalog.product.infrastructure.ProductRepository;
import com.catalog.variant.infrastructure.VariantRepository;
import com.catalog.category.domain.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private VariantRepository variantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldRejectDuplicateSlugOnCreate() {
        when(productRepository.existsBySlug("iphone-15")).thenReturn(true);

        CreateProductRequest request = new CreateProductRequest(
                "iPhone 15",
                "iphone-15",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("iphone-15");
    }

    @Test
    void shouldRejectDeletingActiveProduct() {
        UUID productId = UUID.randomUUID();
        Product product = Product.createDraft("Demo", "demo");
        Category category = Category.createRoot("Root", "root", "desc");
        product.assignPrimaryCategory(category);
        product.transitionTo(ProductStatus.ACTIVE);

        when(productRepository.findActiveById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.deleteProduct(productId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("cannot be deleted");
    }

    @Test
    void shouldRejectActivatingProductWithoutActiveVariants() {
        UUID productId = UUID.randomUUID();
        Product product = Product.createDraft("Demo", "demo");

        when(productRepository.findActiveById(productId)).thenReturn(Optional.of(product));
        when(variantRepository.countActiveByProductId(productId)).thenReturn(0L);

        UpdateProductStatusRequest request = new UpdateProductStatusRequest(ProductStatus.ACTIVE);

        assertThatThrownBy(() -> productService.updateStatus(productId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("at least one ACTIVE variant is required");
    }
}
