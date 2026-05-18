package com.catalog.brand.application;

import com.catalog.brand.api.dto.request.CreateBrandRequest;
import com.catalog.brand.api.dto.request.UpdateBrandRequest;
import com.catalog.brand.api.dto.response.BrandResponse;
import com.catalog.brand.api.dto.response.BrandSummaryResponse;
import com.catalog.brand.api.mapper.BrandMapper;
import com.catalog.brand.domain.Brand;
import com.catalog.brand.event.BrandMutatedEvent;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.common.cache.CacheNames;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.common.response.PagedResponse;
import com.catalog.common.util.SlugUtils;
import com.catalog.product.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final BrandMapper brandMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public BrandResponse createBrand(CreateBrandRequest request) {
        log.debug("Creating brand: {}", request.name());

        String normalizedName = request.name().trim();
        String slug = resolveSlug(request.slug(), normalizedName);

        validateSlugUniqueness(slug, null);
        validateNameUniqueness(normalizedName, null);
        validateFoundedYear(request.foundedYear());

        Brand brand = Brand.create(normalizedName, slug, normalizeNullable(request.description()));
        brand.updateLogoUrl(normalizeNullable(request.logoUrl()));
        brand.updateWebsiteUrl(normalizeNullable(request.websiteUrl()));
        brand.updateCountryOfOrigin(normalizeNullable(request.countryOfOrigin()));
        brand.updateFoundedYear(request.foundedYear());
        if (request.featured() != null) {
            brand.updateFeatured(request.featured());
        }

        Brand saved = brandRepository.save(brand);
        eventPublisher.publishEvent(new BrandMutatedEvent(saved.getId(), saved.getSlug(), null));
        log.info("Created brand id={} name={} slug={}", saved.getId(), saved.getName(), saved.getSlug());
        return brandMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.BRANDS, key = "'id:' + #id")
    public BrandResponse getBrandById(UUID id) {
        return brandMapper.toResponse(findActiveOrThrow(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.BRANDS, key = "'slug:' + #slug")
    public BrandResponse getBrandBySlug(String slug) {
        return brandMapper.toResponse(
            brandRepository.findActiveBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", slug))
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<BrandResponse> getBrands(String search,
                                                    Boolean active,
                                                    Boolean featured,
                                                    String country,
                                                    Pageable pageable) {
        Page<Brand> page = brandRepository.findByFilters(search, active, featured, country, pageable);
        Page<BrandResponse> mapped = page.map(brandMapper::toResponse);
        return PagedResponse.of(mapped);
    }

    @Transactional(readOnly = true)
    public List<BrandSummaryResponse> getFeaturedBrands() {
        return brandRepository.findFeaturedActiveBrands()
                .stream()
                .map(brandMapper::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BrandResponse updateBrand(UUID id, UpdateBrandRequest request) {
        log.debug("Updating brand id={}", id);

        Brand brand = findActiveOrThrow(id);
        String oldSlug = brand.getSlug();
        String normalizedName = request.name().trim();

        String newSlug = resolveSlug(request.slug(), normalizedName);
        boolean slugChanging = !brand.getSlug().equals(newSlug);

        if (slugChanging) {
            guardSlugChange(brand);
            validateSlugUniqueness(newSlug, id);
            log.warn("Slug change on brand id={}: '{}' → '{}'. Existing URLs will break if not redirected.",
                    id, brand.getSlug(), newSlug);
        }

        boolean nameChanging = !brand.getName().equalsIgnoreCase(normalizedName);
        if (nameChanging) {
            validateNameUniqueness(normalizedName, id);
        }

        validateFoundedYear(request.foundedYear());

        brand.updateIdentity(normalizedName, newSlug, normalizeNullable(request.description()));
        brand.updateLogoUrl(normalizeNullable(request.logoUrl()));
        brand.updateWebsiteUrl(normalizeNullable(request.websiteUrl()));
        brand.updateCountryOfOrigin(normalizeNullable(request.countryOfOrigin()));
        brand.updateFoundedYear(request.foundedYear());
        if (request.active() != null) {
            brand.updateActive(request.active());
        }
        if (request.featured() != null) {
            brand.updateFeatured(request.featured());
        }

        Brand saved = brandRepository.save(brand);
        eventPublisher.publishEvent(new BrandMutatedEvent(saved.getId(), saved.getSlug(), oldSlug));
        log.info("Updated brand id={}", saved.getId());
        return brandMapper.toResponse(saved);
    }

    @Transactional
    public void deleteBrand(UUID id) {
        Brand brand = findActiveOrThrow(id);
        String slug = brand.getSlug();

        if (productRepository.countActiveByBrandId(id) > 0) {
            throw new BusinessRuleViolationException(
                "Cannot delete brand '" + brand.getName() +
                "': it has active products. Reassign or archive them first."
            );
        }

        brand.markDeleted();
        brandRepository.save(brand);
        eventPublisher.publishEvent(new BrandMutatedEvent(id, slug, slug));
        log.info("Soft-deleted brand id={} name={}", id, brand.getName());
    }

    private Brand findActiveOrThrow(UUID id) {
        return brandRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", id));
    }

    private String resolveSlug(String requestedSlug, String name) {
        return (requestedSlug != null && !requestedSlug.isBlank())
                ? requestedSlug.trim().toLowerCase()
                : SlugUtils.toSlug(name);
    }

    private void validateSlugUniqueness(String slug, UUID excludeId) {
        boolean conflict = (excludeId == null)
                ? brandRepository.existsBySlug(slug)
                : brandRepository.existsBySlugExcluding(slug, excludeId);

        if (conflict) {
            throw new DuplicateResourceException(
                "A brand with slug '" + slug + "' already exists."
            );
        }
    }

    private void validateNameUniqueness(String name, UUID excludeId) {
        boolean conflict = (excludeId == null)
                ? brandRepository.existsByName(name)
                : brandRepository.existsByNameExcluding(name, excludeId);

        if (conflict) {
            throw new DuplicateResourceException(
                "A brand with name '" + name + "' already exists."
            );
        }
    }

    private void validateFoundedYear(Integer foundedYear) {
        if (foundedYear == null) return;

        int currentYear = Year.now().getValue();
        if (foundedYear > currentYear) {
            throw new BusinessRuleViolationException(
                "Founded year " + foundedYear + " cannot be in the future. Current year: " + currentYear
            );
        }
    }

    private void guardSlugChange(Brand brand) {
        if (productRepository.countActiveByBrandId(brand.getId()) > 0) {
            throw new BusinessRuleViolationException(
                "Cannot change slug for brand '" + brand.getName() +
                "' because it has associated products. Slug changes break existing product URLs."
            );
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
