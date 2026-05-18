package com.catalog.category.application;

import com.catalog.category.api.dto.request.CreateCategoryRequest;
import com.catalog.category.api.dto.request.UpdateCategoryRequest;
import com.catalog.category.api.dto.response.CategoryResponse;
import com.catalog.category.api.dto.response.CategorySummaryResponse;
import com.catalog.category.api.dto.response.CategoryTreeResponse;
import com.catalog.category.api.mapper.CategoryMapper;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.category.event.CategoryMutatedEvent;
import com.catalog.common.cache.CacheNames;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.common.util.SlugUtils;
import com.catalog.product.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.debug("Creating category with name: {}", request.name());

        String normalizedName = request.name().trim();
        String slug = resolveSlug(request.slug(), normalizedName);
        validateSlugUniqueness(slug, null);

        Category parent = null;
        if (request.parentId() != null) {
            parent = findActiveOrThrow(request.parentId());
        }

        Category category = (parent == null)
                ? Category.createRoot(normalizedName, slug, normalizeNullable(request.description()))
                : Category.createChild(normalizedName, slug, normalizeNullable(request.description()), parent);
        category.updateDetails(
                normalizedName,
                slug,
                normalizeNullable(request.description()),
                request.displayOrder(),
                true,
                normalizeNullable(request.imageUrl()),
                normalizeNullable(request.metaTitle()),
                normalizeNullable(request.metaDescription())
        );

        categoryRepository.save(category);
        category.initializePath();
        Category saved = categoryRepository.save(category);

        eventPublisher.publishEvent(new CategoryMutatedEvent(saved.getId(), saved.getSlug(), null));

        log.info("Created category id={} slug={} depth={}", saved.getId(), saved.getSlug(), saved.getDepth());
        return categoryMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.CATEGORIES, key = "'id:' + #id")
    public CategoryResponse getCategoryById(UUID id) {
        return categoryMapper.toResponse(findActiveOrThrow(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.CATEGORIES, key = "'slug:' + #slug")
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findActiveBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", slug));
        return categoryMapper.toResponse(category);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.CATEGORIES, key = "'tree:all'")
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> all = categoryRepository.findAllActive();
        return buildTree(all);
    }

    @Transactional(readOnly = true)
    public List<CategorySummaryResponse> getAncestors(UUID id) {
        Category category = findActiveOrThrow(id);
        List<UUID> ancestorIds = parseAncestorIdsFromPath(category.getPath(), category.getId());

        if (ancestorIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Category> ancestors = categoryRepository.findAncestorsByIds(ancestorIds);
        ancestors.sort(Comparator.comparingInt(Category::getDepth));
        return ancestors.stream().map(categoryMapper::toSummaryResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getDirectChildren(UUID parentId) {
        findActiveOrThrow(parentId);
        return categoryRepository.findActiveChildrenByParentId(parentId)
                .stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryTreeResponse getSubtree(UUID rootId) {
        Category root = findActiveOrThrow(rootId);
        String pathPrefix = root.getPath() + "/";

        List<Category> subtreeNodes = new ArrayList<>();
        subtreeNodes.add(root);
        subtreeNodes.addAll(categoryRepository.findActiveSubtree(pathPrefix));

        List<CategoryTreeResponse> trees = buildTree(subtreeNodes);

        return trees.stream()
                .filter(t -> t.id().equals(rootId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Category", rootId));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        log.debug("Updating category id={}", id);

        Category category = findActiveOrThrow(id);
        String oldSlug = category.getSlug();
        String normalizedName = request.name().trim();

        String slug = resolveSlug(request.slug(), normalizedName);
        validateSlugUniqueness(slug, id);

        boolean parentChanged = !Objects.equals(
                category.getParent() == null ? null : category.getParent().getId(),
                request.parentId()
        );

        if (parentChanged) {
            handleParentChange(category, request.parentId());
        }

        category.updateDetails(
                normalizedName,
                slug,
                normalizeNullable(request.description()),
                request.displayOrder(),
                request.active(),
                normalizeNullable(request.imageUrl()),
                normalizeNullable(request.metaTitle()),
                normalizeNullable(request.metaDescription())
        );

        Category saved = categoryRepository.save(category);
        eventPublisher.publishEvent(new CategoryMutatedEvent(saved.getId(), saved.getSlug(), oldSlug));
        log.info("Updated category id={}", saved.getId());
        return categoryMapper.toResponse(saved);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findActiveOrThrow(id);

        if (categoryRepository.hasActiveChildren(id)) {
            throw new BusinessRuleViolationException(
                "Cannot delete category '" + category.getName() +
                "' because it has active child categories. " +
                "Delete or reassign children first."
            );
        }

        if (productRepository.countActiveByCategoryId(id) > 0) {
            throw new BusinessRuleViolationException(
                "Cannot delete category '" + category.getName() +
                "': it has associated products. Reassign them first."
            );
        }

        category.markDeleted();
        categoryRepository.save(category);
        eventPublisher.publishEvent(new CategoryMutatedEvent(id, category.getSlug(), category.getSlug()));
        log.info("Soft-deleted category id={} slug={}", id, category.getSlug());
    }

    private Category findActiveOrThrow(UUID id) {
        return categoryRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private String resolveSlug(String requestedSlug, String name) {
        return (requestedSlug != null && !requestedSlug.isBlank())
                ? requestedSlug.trim().toLowerCase()
                : SlugUtils.toSlug(name);
    }

    private void validateSlugUniqueness(String slug, UUID excludeId) {
        boolean exists = (excludeId == null)
                ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugExcluding(slug, excludeId);

        if (exists) {
            throw new DuplicateResourceException(
                "A category with slug '" + slug + "' already exists."
            );
        }
    }

    private void handleParentChange(Category category, UUID newParentId) {
        String oldPath = category.getPath();
        int oldDepth = category.getDepth();

        Category newParent = newParentId == null ? null : findActiveOrThrow(newParentId);
        category.moveTo(newParent);

        category.initializePath();
        String newPath = category.getPath();
        int depthDelta = category.getDepth() - oldDepth;

        String oldPrefix = oldPath + "/";
        String newPrefix = newPath + "/";
        String likePattern = oldPath + "/%";

        int updated = depthDelta == 0
                ? categoryRepository.bulkUpdatePath(oldPrefix, newPrefix, likePattern)
                : categoryRepository.bulkUpdatePathAndDepth(oldPrefix, newPrefix, likePattern, depthDelta);
        log.info("Parent change for category id={}: updated {} descendant paths/depths", category.getId(), updated);
    }

    private List<CategoryTreeResponse> buildTree(List<Category> categories) {
        Map<UUID, CategoryTreeResponse> nodeMap = new LinkedHashMap<>();
        List<CategoryTreeResponse> roots = new ArrayList<>();

        for (Category cat : categories) {
            CategoryTreeResponse node = CategoryTreeResponse.of(
                    cat.getId(), cat.getName(), cat.getSlug(),
                    cat.getDepth(), cat.isActive(), cat.getDisplayOrder());
            nodeMap.put(cat.getId(), node);
        }

        for (Category cat : categories) {
            CategoryTreeResponse node = nodeMap.get(cat.getId());
            if (cat.getParent() == null) {
                roots.add(node);
            } else {
                CategoryTreeResponse parentNode = nodeMap.get(cat.getParent().getId());
                if (parentNode != null) {
                    parentNode.children().add(node);
                }
            }
        }

        return roots;
    }

    private List<UUID> parseAncestorIdsFromPath(String path, UUID selfId) {
        String[] parts = path.split("/");
        List<UUID> ids = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) continue;
            UUID parsed = UUID.fromString(part);
            if (!parsed.equals(selfId)) {
                ids.add(parsed);
            }
        }
        return ids;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
