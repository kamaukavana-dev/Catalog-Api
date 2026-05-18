package com.catalog.product.application;

import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.cache.CacheNames;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.InvalidInputException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.common.response.PagedResponse;
import com.catalog.common.util.SlugUtils;
import com.catalog.product.api.dto.request.CreateProductRequest;
import com.catalog.product.api.dto.request.UpdateProductRequest;
import com.catalog.product.api.dto.request.UpdateProductStatusRequest;
import com.catalog.product.api.dto.response.ProductResponse;
import com.catalog.product.api.dto.response.ProductSummaryResponse;
import com.catalog.product.api.mapper.ProductMapper;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductStatus;
import com.catalog.product.event.ProductMutatedEvent;
import com.catalog.product.infrastructure.ProductImageRepository;
import com.catalog.product.infrastructure.ProductRepository;
import com.catalog.variant.infrastructure.VariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final VariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductMapper productMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        log.debug("Creating product: {}", request.name());

        String slug = resolveSlug(request.slug(), request.name());
        validateSlugUniqueness(slug, null);

        Product product = Product.createDraft(request.name(), slug);

        product.updateContent(
                request.shortDescription(),
                request.description(),
                request.metaTitle(),
                request.metaDescription()
        );

        if (request.primaryCategoryId() != null) {
            product.assignPrimaryCategory(findActiveCategoryOrThrow(request.primaryCategoryId()));
        }
        if (request.brandId() != null) {
            product.assignBrand(findActiveBrandOrThrow(request.brandId()));
        }

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductMutatedEvent(saved.getId(), saved.getSlug(), null, false));
        log.info("Created product id={} slug={} status={}", saved.getId(), saved.getSlug(), saved.getStatus());
        return productMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.PRODUCTS, key = "'id:' + #id")
    public ProductResponse getProductById(UUID id) {
        return productMapper.toResponse(findActiveByIdWithDetailsOrThrow(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.PRODUCTS, key = "'slug:' + #slug")
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findActiveBySlugWithDetails(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", slug));
        return productMapper.toResponse(product);
    }

    public ProductSummaryResponse toSummaryResponse(Product product) {
        return productMapper.toSummaryResponse(product);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductSummaryResponse> listProducts(ProductStatus status,
                                                              UUID brandId,
                                                              UUID primaryCategoryId,
                                                              String search,
                                                              Pageable pageable) {
        Page<Product> page = productRepository.findByFilters(status, brandId, primaryCategoryId, search, pageable);
        Page<ProductSummaryResponse> mapped = page.map(productMapper::toSummaryResponse);
        return PagedResponse.of(mapped);
    }

    @Transactional
    public ProductResponse updateStatus(UUID id, UpdateProductStatusRequest request) {
        log.debug("Status transition request for product id={}: -> {}", id, request.targetStatus());

        Product product = productRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        ProductStatus from = product.getStatus();
        String oldSlug = product.getSlug();

        if (request.targetStatus() == ProductStatus.ACTIVE) {
            // Precondition 1: at least one ACTIVE variant
            long activeVariantCount = variantRepository.countActiveByProductId(id);
            if (activeVariantCount == 0) {
                throw new BusinessRuleViolationException(
                    "Cannot activate product '" + product.getName() +
                    "': at least one ACTIVE variant is required.");
            }

            // Precondition 2: at least one READY image
            if (!imageRepository.hasReadyImages(id)) {
                throw new BusinessRuleViolationException(
                    "Cannot activate product '" + product.getName() +
                    "': at least one uploaded and processed image is required.");
            }

            product.transitionTo(request.targetStatus());

        } else {
            product.transitionTo(request.targetStatus());
        }

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductMutatedEvent(saved.getId(), saved.getSlug(), oldSlug, true));
        log.info("Product id={} status: {} -> {}", id, from, saved.getStatus());
        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        log.debug("Updating product id={}", id);

        Product product = findActiveByIdWithDetailsOrThrow(id);
        String oldSlug = product.getSlug();

        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new BusinessRuleViolationException(
                    "Archived product '" + product.getName() + "' cannot be modified."
            );
        }

        String newSlug = resolveSlug(request.slug(), request.name());
        if (!product.getSlug().equals(newSlug)) {
            validateSlugUniqueness(newSlug, id);
        }

        product.updateName(request.name(), newSlug);
        product.updateContent(
                request.shortDescription(),
                request.description(),
                request.metaTitle(),
                request.metaDescription()
        );

        if (request.primaryCategoryId() != null) {
            product.assignPrimaryCategory(findActiveCategoryOrThrow(request.primaryCategoryId()));
        } else {
            product.assignPrimaryCategory(null);
        }

        if (request.brandId() != null) {
            product.assignBrand(findActiveBrandOrThrow(request.brandId()));
        } else {
            product.assignBrand(null);
        }

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductMutatedEvent(saved.getId(), saved.getSlug(), oldSlug, false));
        log.info("Updated product id={}", saved.getId());
        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse addSecondaryCategory(UUID productId, UUID categoryId) {
        Product product = findActiveByIdWithDetailsOrThrow(productId);
        Category category = findActiveCategoryOrThrow(categoryId);

        product.addSecondaryCategory(category);

        Product saved = productRepository.save(product);
        log.info("Added secondary category {} to product {}", categoryId, productId);
        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse removeSecondaryCategory(UUID productId, UUID categoryId) {
        Product product = findActiveByIdWithDetailsOrThrow(productId);
        Category category = findActiveCategoryOrThrow(categoryId);

        product.removeSecondaryCategory(category);

        Product saved = productRepository.save(product);
        log.info("Removed secondary category {} from product {}", categoryId, productId);
        return productMapper.toResponse(saved);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        String slug = product.getSlug();

        if (!product.isDeletable()) {
            throw new BusinessRuleViolationException(
                    "Product '" + product.getName() + "' cannot be deleted in status " +
                            product.getStatus() + "."
            );
        }

        product.markDeleted();
        productRepository.save(product);
        eventPublisher.publishEvent(new ProductMutatedEvent(id, slug, slug, true));
        log.info("Soft-deleted product id={} name={}", id, product.getName());
    }

    private Product findActiveByIdWithDetailsOrThrow(UUID id) {
        return productRepository.findActiveByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category findActiveCategoryOrThrow(UUID categoryId) {
        return categoryRepository.findActiveById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private Brand findActiveBrandOrThrow(UUID brandId) {
        return brandRepository.findActiveById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", brandId));
    }

    private String resolveSlug(String requested, String name) {
        return (requested != null && !requested.isBlank())
                ? requested.trim().toLowerCase()
                : SlugUtils.toSlug(name);
    }

    private void validateSlugUniqueness(String slug, UUID excludeId) {
        boolean exists = (excludeId == null)
                ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugExcluding(slug, excludeId);
        if (exists) {
            throw new DuplicateResourceException("A product with slug '" + slug + "' already exists.");
        }
    }
}
