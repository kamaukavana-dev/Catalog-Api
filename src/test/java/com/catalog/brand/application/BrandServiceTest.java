package com.catalog.brand.application;

import com.catalog.brand.api.dto.request.CreateBrandRequest;
import com.catalog.brand.api.dto.request.UpdateBrandRequest;
import com.catalog.brand.api.mapper.BrandMapper;
import com.catalog.brand.api.dto.response.BrandResponse;
import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock private BrandRepository brandRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BrandMapper brandMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private BrandService brandService;

    @Test
    void shouldRejectDuplicateSlug() {
        when(brandRepository.existsBySlug("nike")).thenReturn(true);

        CreateBrandRequest request = new CreateBrandRequest(
            "Nike", "nike", null, null, null, null, null, null
        );

        assertThatThrownBy(() -> brandService.createBrand(request))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("nike");
    }

    @Test
    void shouldRejectDuplicateName() {
        when(brandRepository.existsBySlug("nike-alt")).thenReturn(false);
        when(brandRepository.existsByName("Nike")).thenReturn(true);

        CreateBrandRequest request = new CreateBrandRequest(
            "Nike", "nike-alt", null, null, null, null, null, null
        );

        assertThatThrownBy(() -> brandService.createBrand(request))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("Nike");
    }

    @Test
    void shouldRejectFoundedYearInFuture() {
        when(brandRepository.existsBySlug("future-brand")).thenReturn(false);
        when(brandRepository.existsByName("FutureBrand")).thenReturn(false);

        int futureYear = java.time.Year.now().getValue() + 1;

        CreateBrandRequest request = new CreateBrandRequest(
            "FutureBrand", "future-brand", null, null, null, null, futureYear, null
        );

        assertThatThrownBy(() -> brandService.createBrand(request))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("future");
    }

    @Test
    void shouldApplyFieldUpdates() {
        UUID id = UUID.randomUUID();
        Brand brand = Brand.create("Old Brand", "old-brand", "old");
        brand.setId(id);

        when(brandRepository.findActiveById(id)).thenReturn(Optional.of(brand));
        when(brandRepository.existsBySlugExcluding("new-brand", id)).thenReturn(false);
        when(brandRepository.existsByNameExcluding("New Brand", id)).thenReturn(false);
        when(productRepository.countActiveByBrandId(id)).thenReturn(0L);
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(brandMapper.toResponse(any(Brand.class))).thenAnswer(invocation -> {
            Brand saved = invocation.getArgument(0);
            return new BrandResponse(
                    saved.getId(),
                    saved.getName(),
                    saved.getSlug(),
                    saved.getDescription(),
                    saved.getLogoUrl(),
                    saved.getWebsiteUrl(),
                    saved.getCountryOfOrigin(),
                    saved.getFoundedYear(),
                    saved.isActive(),
                    saved.isFeatured(),
                    Instant.now(),
                    Instant.now()
            );
        });

        UpdateBrandRequest request = new UpdateBrandRequest(
                "New Brand",
                "new-brand",
                "new-desc",
                "https://cdn/logo.png",
                "https://brand.example",
                "US",
                1999,
                false,
                true
        );

        brandService.updateBrand(id, request);

        assertThat(brand.getName()).isEqualTo("New Brand");
        assertThat(brand.getSlug()).isEqualTo("new-brand");
        assertThat(brand.getDescription()).isEqualTo("new-desc");
        assertThat(brand.getLogoUrl()).isEqualTo("https://cdn/logo.png");
        assertThat(brand.getWebsiteUrl()).isEqualTo("https://brand.example");
        assertThat(brand.getCountryOfOrigin()).isEqualTo("US");
        assertThat(brand.getFoundedYear()).isEqualTo(1999);
        assertThat(brand.isActive()).isFalse();
        assertThat(brand.isFeatured()).isTrue();
    }
}
