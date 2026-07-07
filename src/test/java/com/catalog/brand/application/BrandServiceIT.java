package com.catalog.brand.application;

import com.catalog.brand.api.dto.request.CreateBrandRequest;
import com.catalog.brand.api.dto.request.UpdateBrandRequest;
import com.catalog.brand.api.dto.response.BrandResponse;
import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.product.domain.Product;
import com.catalog.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Year;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandServiceIT extends BaseIntegrationTest {

    @Autowired
    private BrandService brandService;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;

    private CreateBrandRequest create(String name, String slug) {
        return new CreateBrandRequest(name, slug, "desc", null, null, "US", 1990, false);
    }

    @Test
    void createsBrandAndPersistsIt() {
        BrandResponse res = brandService.createBrand(create("Nikon", "nikon"));

        assertThat(res.name()).isEqualTo("Nikon");
        assertThat(res.slug()).isEqualTo("nikon");
        Brand saved = brandRepository.findActiveById(res.id()).orElseThrow();
        assertThat(saved.getCountryOfOrigin()).isEqualTo("US");
        assertThat(saved.getFoundedYear()).isEqualTo(1990);
    }

    @Test
    void derivesSlugFromNameWhenSlugBlank() {
        BrandResponse res = brandService.createBrand(create("Sony Alpha", null));
        assertThat(res.slug()).isEqualTo("sony-alpha");
    }

    @Test
    void rejectsDuplicateSlug() {
        brandService.createBrand(create("Canon", "canon"));
        assertThatThrownBy(() -> brandService.createBrand(create("Canon Two", "canon")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void rejectsDuplicateName() {
        brandService.createBrand(create("Fuji", "fuji"));
        assertThatThrownBy(() -> brandService.createBrand(create("Fuji", "fuji-2")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsFoundedYearInTheFuture() {
        int future = Year.now().getValue() + 1;
        assertThatThrownBy(() -> brandService.createBrand(
                new CreateBrandRequest("Future", "future", "d", null, null, "US", future, false)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("cannot be in the future");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        assertThatThrownBy(() -> brandService.getBrandById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getBySlugReturnsAndThrowsWhenMissing() {
        brandService.createBrand(create("Leica", "leica"));
        assertThat(brandService.getBrandBySlug("leica").name()).isEqualTo("Leica");
        assertThatThrownBy(() -> brandService.getBrandBySlug("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatesNameAndSlug() {
        BrandResponse created = brandService.createBrand(create("Olympus", "olympus"));

        BrandResponse updated = brandService.updateBrand(created.id(), new UpdateBrandRequest(
                "Olympus Pro", "olympus-pro", "d2", null, null, "JP", 1919, true, true));

        assertThat(updated.name()).isEqualTo("Olympus Pro");
        assertThat(updated.slug()).isEqualTo("olympus-pro");
        assertThat(brandRepository.findActiveById(created.id()).orElseThrow().getCountryOfOrigin()).isEqualTo("JP");
    }

    @Test
    void updateRejectsSlugCollisionWithAnotherBrand() {
        brandService.createBrand(create("Pentax", "pentax"));
        BrandResponse other = brandService.createBrand(create("Sigma", "sigma"));

        assertThatThrownBy(() -> brandService.updateBrand(other.id(), new UpdateBrandRequest(
                "Sigma", "pentax", "d", null, null, "US", null, true, false)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void softDeletesBrandWithNoProducts() {
        BrandResponse created = brandService.createBrand(create("Tamron", "tamron"));

        brandService.deleteBrand(created.id());

        assertThat(brandRepository.findActiveById(created.id())).isEmpty();
    }

    @Test
    void refusesToDeleteBrandThatStillHasProducts() {
        BrandResponse created = brandService.createBrand(create("Zeiss", "zeiss"));
        Brand brand = brandRepository.findActiveById(created.id()).orElseThrow();
        Product p = Product.createDraft("Lens", "lens-" + UUID.randomUUID());
        p.assignBrand(brand);
        productRepository.save(p);

        assertThatThrownBy(() -> brandService.deleteBrand(created.id()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("active products");
    }

    @Test
    void featuredBrandsListReturnsOnlyFeaturedActiveOnes() {
        brandService.createBrand(new CreateBrandRequest("Plain", "plain", "d", null, null, "US", null, false));
        brandService.createBrand(new CreateBrandRequest("Star", "star", "d", null, null, "US", null, true));

        assertThat(brandService.getFeaturedBrands())
                .extracting(com.catalog.brand.api.dto.response.BrandSummaryResponse::name)
                .containsExactly("Star");
    }
}
