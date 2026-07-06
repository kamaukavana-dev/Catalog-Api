package com.catalog.product.application.search;

import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductStatus;
import com.catalog.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAdminQueryServiceIT extends BaseIntegrationTest {

    @Autowired
    private ProductAdminQueryService adminQueryService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private Brand acme;
    private Brand globex;
    private Category electronics;
    private Category books;

    @BeforeEach
    void seed() {
        acme = brandRepository.save(Brand.create("Acme", "acme-" + UUID.randomUUID(), "d"));
        globex = brandRepository.save(Brand.create("Globex", "globex-" + UUID.randomUUID(), "d"));
        electronics = categoryRepository.save(Category.createRoot("Electronics", "electronics-" + UUID.randomUUID(), "d"));
        books = categoryRepository.save(Category.createRoot("Books", "books-" + UUID.randomUUID(), "d"));

        product("Alpha", acme, electronics, ProductStatus.DRAFT);
        product("Bravo", acme, electronics, ProductStatus.ACTIVE);
        product("Charlie", globex, books, ProductStatus.DRAFT);
    }

    private void product(String name, Brand brand, Category category, ProductStatus status) {
        Product p = Product.createDraft(name, name.toLowerCase() + "-" + UUID.randomUUID());
        p.assignBrand(brand);
        p.assignPrimaryCategory(category);
        if (status == ProductStatus.ACTIVE) {
            p.transitionTo(ProductStatus.ACTIVE);
        }
        productRepository.save(p);
    }

    private AdminProductFilterParams params(Set<ProductStatus> statuses, UUID categoryId, UUID brandId,
                                            String search, SortOption sort) {
        return new AdminProductFilterParams(statuses, categoryId, brandId, search, sort, 0, 50);
    }

    @Test
    void listsAllNonDeletedProductsWhenUnfiltered() {
        Page<Product> page = adminQueryService.adminList(
                params(null, null, null, null, SortOption.NEWEST), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Product::getName)
                .containsExactlyInAnyOrder("Alpha", "Bravo", "Charlie");
    }

    @Test
    void filtersByStatus() {
        Page<Product> page = adminQueryService.adminList(
                params(Set.of(ProductStatus.ACTIVE), null, null, null, SortOption.NEWEST), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Bravo");
    }

    @Test
    void filtersByBrand() {
        Page<Product> page = adminQueryService.adminList(
                params(null, null, acme.getId(), null, SortOption.NAME_ASC), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Alpha", "Bravo");
    }

    @Test
    void filtersByCategory() {
        Page<Product> page = adminQueryService.adminList(
                params(null, books.getId(), null, null, SortOption.NEWEST), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Charlie");
    }

    @Test
    void filtersBySearchTermOnName() {
        Page<Product> page = adminQueryService.adminList(
                params(null, null, null, "alph", SortOption.NEWEST), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Alpha");
    }

    @Test
    void combinesStatusAndBrandFilters() {
        Page<Product> page = adminQueryService.adminList(
                params(Set.of(ProductStatus.ACTIVE), null, acme.getId(), null, SortOption.NEWEST), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Bravo");
    }

    @Test
    void sortsByNameAscendingAndDescending() {
        Page<Product> asc = adminQueryService.adminList(
                params(null, null, null, null, SortOption.NAME_ASC), PageRequest.of(0, 10));
        assertThat(asc.getContent()).extracting(Product::getName).containsExactly("Alpha", "Bravo", "Charlie");

        Page<Product> desc = adminQueryService.adminList(
                params(null, null, null, null, SortOption.NAME_DESC), PageRequest.of(0, 10));
        assertThat(desc.getContent()).extracting(Product::getName).containsExactly("Charlie", "Bravo", "Alpha");
    }

    @Test
    void paginatesWithPageSizeOne() {
        Page<Product> firstPage = adminQueryService.adminList(
                params(null, null, null, null, SortOption.NAME_ASC), PageRequest.of(0, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(Product::getName).containsExactly("Alpha");

        Page<Product> secondPage = adminQueryService.adminList(
                params(null, null, null, null, SortOption.NAME_ASC), PageRequest.of(1, 1));
        assertThat(secondPage.getContent()).extracting(Product::getName).containsExactly("Bravo");
    }

    @Test
    void returnsEmptyPageWhenNothingMatches() {
        Page<Product> page = adminQueryService.adminList(
                params(null, null, null, "no-such-product", SortOption.NEWEST), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }
}
