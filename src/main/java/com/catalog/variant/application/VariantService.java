package com.catalog.variant.application;

import com.catalog.attribute.domain.AttributeValue;
import com.catalog.attribute.infrastructure.AttributeValueRepository;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.common.util.SkuGenerator;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductStatus;
import com.catalog.product.infrastructure.ProductRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.variant.api.dto.request.CreateVariantRequest;
import com.catalog.variant.api.dto.request.UpdateVariantRequest;
import com.catalog.variant.api.dto.request.UpdateVariantStatusRequest;
import com.catalog.variant.api.dto.response.VariantResponse;
import com.catalog.variant.api.dto.response.VariantSummaryResponse;
import com.catalog.variant.api.mapper.VariantMapper;
import com.catalog.variant.domain.Variant;
import com.catalog.variant.domain.VariantStatus;
import com.catalog.variant.event.VariantMutatedEvent;
import com.catalog.variant.infrastructure.VariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VariantService {

    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final InventoryRepository inventoryRepository;
    private final VariantMapper variantMapper;
    private final SkuGenerator skuGenerator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public VariantResponse createVariant(UUID productId, CreateVariantRequest request) {
        log.debug("Creating variant for product id={}", productId);

        Product product = findActiveProductOrThrow(productId);

        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new BusinessRuleViolationException(
                    "Cannot add variants to archived product '" + product.getName() + "'."
            );
        }

        Set<AttributeValue> attributeValues = resolveAndValidateAttributes(request.attributeValueIds());
        assertAttributeCombinationUnique(productId, request.attributeValueIds(), null);

        String internalSku = generateUniqueSku();

        if (request.merchantSku() != null && variantRepository.existsByMerchantSku(request.merchantSku())) {
            throw new DuplicateResourceException(
                    "Merchant SKU '" + request.merchantSku() + "' is already in use."
            );
        }

        Variant variant = Variant.createDraft(product, internalSku, request.basePrice(), request.taxClass());
        variant.setMerchantSku(request.merchantSku());
        variant.setCostPrice(request.costPrice());
        variant.setSalePrice(request.salePrice(), request.saleStartAt(), request.saleEndAt());
        applyDimensions(variant, request);
        variant.getAttributeValues().addAll(attributeValues);

        Variant saved = variantRepository.save(variant);
        eventPublisher.publishEvent(new VariantMutatedEvent(saved.getId(), saved.getProduct().getId()));
        log.info("Created variant id={} sku={} for product id={}", saved.getId(), saved.getInternalSku(), productId);
        return variantMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VariantResponse getVariantById(UUID productId, UUID variantId) {
        Variant variant = findActiveVariantOrThrow(variantId);
        assertVariantBelongsToProduct(variant, productId);
        return variantMapper.toResponse(variant);
    }

    @Transactional(readOnly = true)
    public List<VariantSummaryResponse> getVariantsForProduct(UUID productId) {
        findActiveProductOrThrow(productId);
        return variantRepository.findActiveByProductIdWithAttributes(productId)
                .stream()
                .map(variantMapper::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public VariantResponse updateStatus(UUID productId, UUID variantId, UpdateVariantStatusRequest request) {
        Variant variant = findActiveVariantOrThrow(variantId);
        assertVariantBelongsToProduct(variant, productId);

        VariantStatus from = variant.getStatus();
        variant.transitionTo(request.targetStatus());

        Variant saved = variantRepository.save(variant);
        eventPublisher.publishEvent(new VariantMutatedEvent(saved.getId(), saved.getProduct().getId()));
        log.info("Variant id={} status: {} -> {}", variantId, from, saved.getStatus());
        return variantMapper.toResponse(saved);
    }

    @Transactional
    public VariantResponse updateVariant(UUID productId, UUID variantId, UpdateVariantRequest request) {
        Variant variant = findActiveVariantOrThrow(variantId);
        assertVariantBelongsToProduct(variant, productId);

        if (variant.getStatus() == VariantStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("Archived variant cannot be modified.");
        }

        if (request.attributeValueIds() != null) {
            Set<AttributeValue> newAttributes = resolveAndValidateAttributes(request.attributeValueIds());
            assertAttributeCombinationUnique(productId, request.attributeValueIds(), variantId);
            variant.getAttributeValues().clear();
            variant.getAttributeValues().addAll(newAttributes);
        }

        if (request.merchantSku() != null && !request.merchantSku().equals(variant.getMerchantSku())) {
            if (variantRepository.existsByMerchantSkuExcluding(request.merchantSku(), variantId)) {
                throw new DuplicateResourceException(
                        "Merchant SKU '" + request.merchantSku() + "' is already in use."
                );
            }
            variant.setMerchantSku(request.merchantSku());
        }

        if (request.basePrice() != null) {
            variant.setBasePrice(request.basePrice());
        }
        if (request.taxClass() != null) {
            variant.setTaxClass(request.taxClass());
        }
        if (request.costPrice() != null) {
            variant.setCostPrice(request.costPrice());
        }

        variant.setSalePrice(request.salePrice(), request.saleStartAt(), request.saleEndAt());
        applyDimensions(variant, request);

        Variant saved = variantRepository.save(variant);
        eventPublisher.publishEvent(new VariantMutatedEvent(saved.getId(), saved.getProduct().getId()));
        log.info("Updated variant id={}", variantId);
        return variantMapper.toResponse(saved);
    }

    @Transactional
    public void deleteVariant(UUID productId, UUID variantId) {
        Variant variant = findActiveVariantOrThrow(variantId);
        assertVariantBelongsToProduct(variant, productId);

        if (variant.getStatus() == VariantStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "Cannot delete an ACTIVE variant. Transition to DISCONTINUED first."
            );
        }

        if (inventoryRepository.hasStockForVariant(variantId)) {
            throw new BusinessRuleViolationException(
                    "Cannot delete variant with existing stock. Reduce inventory to zero at all warehouses before deletion."
            );
        }

        variant.markDeleted();
        variantRepository.save(variant);
        eventPublisher.publishEvent(new VariantMutatedEvent(variant.getId(), variant.getProduct().getId()));
        log.info("Soft-deleted variant id={} sku={}", variantId, variant.getInternalSku());
    }

    private void assertAttributeCombinationUnique(UUID productId, Set<UUID> newAttributeIds, UUID excludeVariantId) {
        List<Variant> existingVariants = variantRepository.findActiveByProductIdWithAttributes(productId);

        for (Variant existing : existingVariants) {
            if (existing.getId().equals(excludeVariantId)) {
                continue;
            }

            Set<UUID> existingIds = existing.getAttributeValues()
                    .stream()
                    .map(AttributeValue::getId)
                    .collect(Collectors.toSet());

            if (existingIds.equals(newAttributeIds)) {
                throw new BusinessRuleViolationException(
                        "A variant with this exact attribute combination already exists for this product. " +
                                "Duplicate combinations are not allowed."
                );
            }
        }
    }

    private Set<AttributeValue> resolveAndValidateAttributes(Set<UUID> attributeValueIds) {
        if (attributeValueIds == null || attributeValueIds.isEmpty()) {
            return Set.of();
        }

        Set<AttributeValue> values = attributeValueRepository.findActiveByIds(attributeValueIds);

        if (values.size() != attributeValueIds.size()) {
            Set<UUID> foundIds = values.stream()
                    .map(AttributeValue::getId)
                    .collect(Collectors.toSet());
            Set<UUID> missing = attributeValueIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toSet());
            throw new ResourceNotFoundException("AttributeValue", missing);
        }

        long uniqueTypeCount = values.stream()
                .map(av -> av.getAttributeType().getId())
                .distinct()
                .count();

        if (uniqueTypeCount != values.size()) {
            throw new BusinessRuleViolationException(
                    "A variant cannot have multiple values for the same attribute type. " +
                            "For example, a variant cannot be both Blue and Red."
            );
        }

        return values;
    }

    private String generateUniqueSku() {
        for (int attempt = 0; attempt < 3; attempt++) {
            String sku = skuGenerator.generate();
            if (!variantRepository.existsByInternalSku(sku)) {
                return sku;
            }
            log.warn("SKU collision on attempt {}. Regenerating.", attempt + 1);
        }
        throw new IllegalStateException("Failed to generate unique SKU after 3 attempts.");
    }

    private Product findActiveProductOrThrow(UUID productId) {
        return productRepository.findActiveById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private Variant findActiveVariantOrThrow(UUID variantId) {
        return variantRepository.findActiveByIdWithAttributes(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant", variantId));
    }

    private void assertVariantBelongsToProduct(Variant variant, UUID productId) {
        if (!variant.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Variant", variant.getId());
        }
    }

    private void applyDimensions(Variant variant, CreateVariantRequest req) {
        variant.setWeightGrams(req.weightGrams());
        variant.setLengthMm(req.lengthMm());
        variant.setWidthMm(req.widthMm());
        variant.setHeightMm(req.heightMm());
    }

    private void applyDimensions(Variant variant, UpdateVariantRequest req) {
        if (req.weightGrams() != null) {
            variant.setWeightGrams(req.weightGrams());
        }
        if (req.lengthMm() != null) {
            variant.setLengthMm(req.lengthMm());
        }
        if (req.widthMm() != null) {
            variant.setWidthMm(req.widthMm());
        }
        if (req.heightMm() != null) {
            variant.setHeightMm(req.heightMm());
        }
    }
}

