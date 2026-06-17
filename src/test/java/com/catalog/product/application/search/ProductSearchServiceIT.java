package com.catalog.product.application.search;

import com.catalog.common.BaseIntegrationTest;
import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.product.domain.Product;
import com.catalog.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ProductSearchServiceIT extends BaseIntegrationTest {

    @Autowired
    private ProductSearchService searchService;

    @Autowired
    private ProductSearchProjectionService projectionService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM product_search_projection");
        productRepository.deleteAll();
        brandRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void shouldFindProductByName() {
        Product p = Product.createDraft("Awesome Widget", "awesome-widget-" + UUID.randomUUID());
        p.assignPrimaryCategory(categoryRepository.save(Category.createRoot("C", "c-" + UUID.randomUUID(), "D")));
        p.assignBrand(brandRepository.save(Brand.create("B", "b-" + UUID.randomUUID(), "D")));
        p.transitionTo(com.catalog.product.domain.ProductStatus.ACTIVE);
        p = productRepository.save(p);
        projectionService.refreshProjection(p.getId());

        ProductFilterParams params = new ProductFilterParams(
                null, null, null, null, Set.of(), null, "Widget", null, null, 10);
        
        CursorPage<ProductCardDto> result = searchService.search(params);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Awesome Widget");
    }

    @Test
    void shouldFilterByBrand() {
        Brand b1 = brandRepository.save(Brand.create("Brand 1", "b1-" + UUID.randomUUID(), "D"));
        Brand b2 = brandRepository.save(Brand.create("Brand 2", "b2-" + UUID.randomUUID(), "D"));

        Category cat = categoryRepository.save(Category.createRoot("C", "c-" + UUID.randomUUID(), "D"));

        Product p1 = Product.createDraft("P1", "p1-" + UUID.randomUUID());
        p1.assignBrand(b1);
        p1.assignPrimaryCategory(cat);
        p1.transitionTo(com.catalog.product.domain.ProductStatus.ACTIVE);
        productRepository.save(p1);

        Product p2 = Product.createDraft("P2", "p2-" + UUID.randomUUID());
        p2.assignBrand(b2);
        p2.assignPrimaryCategory(cat);
        p2.transitionTo(com.catalog.product.domain.ProductStatus.ACTIVE);
        productRepository.save(p2);

        projectionService.refreshProjection(p1.getId());
        projectionService.refreshProjection(p2.getId());

        ProductFilterParams params = new ProductFilterParams(
                null, b1.getId(), null, null, Set.of(), null, null, null, null, 10);

        CursorPage<ProductCardDto> result = searchService.search(params);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(p1.getId());
    }

    @Test
    void shouldReturnEmptyPage_whenNoResultsMatch() {
        ProductFilterParams params = new ProductFilterParams(
                null, null, null, null, Set.of(), null, "NoneMatch", null, null, 10);

        CursorPage<ProductCardDto> result = searchService.search(params);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.isHasMore()).isFalse();
    }
}
