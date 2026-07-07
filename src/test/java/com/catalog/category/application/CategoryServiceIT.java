package com.catalog.category.application;

import com.catalog.category.api.dto.request.CreateCategoryRequest;
import com.catalog.category.api.dto.request.UpdateCategoryRequest;
import com.catalog.category.api.dto.response.CategoryResponse;
import com.catalog.category.api.dto.response.CategoryTreeResponse;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavior-asserting integration tests for {@link CategoryService} exercised against a
 * real PostgreSQL database (Testcontainers) via {@link BaseIntegrationTest}. Every test
 * asserts a concrete outcome: returned DTO fields, thrown exception type + message, or
 * persisted DB state read back through {@link CategoryRepository}.
 */
class CategoryServiceIT extends BaseIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    // ---- helpers ---------------------------------------------------------

    private CreateCategoryRequest createRequest(String name, String slug, UUID parentId) {
        return new CreateCategoryRequest(name, slug, null, parentId, null, null, null, null);
    }

    private CreateCategoryRequest createRequest(String name, String slug, UUID parentId, Integer displayOrder) {
        return new CreateCategoryRequest(name, slug, null, parentId, displayOrder, null, null, null);
    }

    // ---- create: root ----------------------------------------------------

    @Test
    void shouldCreateRootCategory_withDerivedPathDepthAndDefaults() {
        CreateCategoryRequest request = new CreateCategoryRequest(
                "Electronics", "electronics", "All electronic goods", null, 5,
                "http://img/e.png", "Electronics Meta", "Buy electronics");

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Electronics");
        assertThat(response.slug()).isEqualTo("electronics");
        assertThat(response.description()).isEqualTo("All electronic goods");
        assertThat(response.parentId()).isNull();
        assertThat(response.depth()).isZero();
        assertThat(response.displayOrder()).isEqualTo(5);
        assertThat(response.active()).isTrue();
        assertThat(response.imageUrl()).isEqualTo("http://img/e.png");
        assertThat(response.metaTitle()).isEqualTo("Electronics Meta");
        assertThat(response.metaDescription()).isEqualTo("Buy electronics");

        Category persisted = categoryRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.getPath()).isEqualTo("/" + response.id());
        assertThat(persisted.getDepth()).isZero();
        assertThat(persisted.getParent()).isNull();
        assertThat(persisted.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    void shouldDeriveSlugFromName_whenSlugOmitted() {
        CategoryResponse response = categoryService.createCategory(createRequest("Home & Garden", null, null));

        assertThat(response.slug()).isEqualTo("home-garden");
        assertThat(categoryRepository.existsBySlug("home-garden")).isTrue();
    }

    // ---- create: child ---------------------------------------------------

    @Test
    void shouldCreateChildCategory_withPathAndDepthDerivedFromParent() {
        CategoryResponse parent = categoryService.createCategory(createRequest("Electronics", "electronics", null));

        CategoryResponse child = categoryService.createCategory(createRequest("Phones", "phones", parent.id()));

        assertThat(child.parentId()).isEqualTo(parent.id());
        assertThat(child.depth()).isEqualTo(1);

        Category persistedChild = categoryRepository.findById(child.id()).orElseThrow();
        Category persistedParent = categoryRepository.findById(parent.id()).orElseThrow();
        assertThat(persistedChild.getPath())
                .isEqualTo(persistedParent.getPath() + "/" + child.id());
        assertThat(persistedChild.getPath()).isEqualTo("/" + parent.id() + "/" + child.id());
        assertThat(persistedChild.getDepth()).isEqualTo(1);
        assertThat(persistedChild.getParent().getId()).isEqualTo(parent.id());
    }

    @Test
    void shouldCreateGrandchild_withDepthTwoAndCumulativePath() {
        CategoryResponse root = categoryService.createCategory(createRequest("Electronics", "electronics", null));
        CategoryResponse child = categoryService.createCategory(createRequest("Phones", "phones", root.id()));

        CategoryResponse grandchild = categoryService.createCategory(createRequest("Smartphones", "smartphones", child.id()));

        assertThat(grandchild.depth()).isEqualTo(2);
        Category persisted = categoryRepository.findById(grandchild.id()).orElseThrow();
        assertThat(persisted.getPath())
                .isEqualTo("/" + root.id() + "/" + child.id() + "/" + grandchild.id());
    }

    // ---- create: rejections ---------------------------------------------

    @Test
    void shouldRejectDuplicateSlug_onCreate() {
        categoryService.createCategory(createRequest("Electronics", "electronics", null));

        assertThatThrownBy(() -> categoryService.createCategory(createRequest("Electronics 2", "electronics", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("electronics");

        // The rejected create must not have persisted a second row.
        assertThat(categoryRepository.findAllActive()).hasSize(1);
    }

    @Test
    void shouldRejectCreate_whenParentDoesNotExist() {
        UUID missingParent = UUID.randomUUID();

        assertThatThrownBy(() -> categoryService.createCategory(createRequest("Orphan", "orphan", missingParent)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingParent.toString());
    }

    // ---- get by id -------------------------------------------------------

    @Test
    void shouldGetCategoryById() {
        CategoryResponse created = categoryService.createCategory(createRequest("Electronics", "electronics", null));

        CategoryResponse fetched = categoryService.getCategoryById(created.id());

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.name()).isEqualTo("Electronics");
        assertThat(fetched.slug()).isEqualTo("electronics");
    }

    @Test
    void shouldThrowNotFound_whenGetByUnknownId() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> categoryService.getCategoryById(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void shouldGetCategoryBySlug() {
        categoryService.createCategory(createRequest("Electronics", "electronics", null));

        CategoryResponse fetched = categoryService.getCategoryBySlug("electronics");

        assertThat(fetched.slug()).isEqualTo("electronics");
        assertThat(fetched.name()).isEqualTo("Electronics");
    }

    @Test
    void shouldThrowNotFound_whenGetByUnknownSlug() {
        assertThatThrownBy(() -> categoryService.getCategoryBySlug("no-such-slug"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no-such-slug");
    }

    // ---- tree ------------------------------------------------------------

    @Test
    void shouldBuildCategoryTree_nestingChildrenUnderParents() {
        CategoryResponse root = categoryService.createCategory(createRequest("Electronics", "electronics", null));
        CategoryResponse child = categoryService.createCategory(createRequest("Phones", "phones", root.id()));

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();

        assertThat(tree).hasSize(1);
        CategoryTreeResponse rootNode = tree.get(0);
        assertThat(rootNode.id()).isEqualTo(root.id());
        assertThat(rootNode.depth()).isZero();
        assertThat(rootNode.children()).hasSize(1);
        assertThat(rootNode.children().get(0).id()).isEqualTo(child.id());
        assertThat(rootNode.children().get(0).depth()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyTree_whenNoCategories() {
        assertThat(categoryService.getCategoryTree()).isEmpty();
    }

    @Test
    void shouldGetSubtree_rootedAtGivenNode() {
        CategoryResponse root = categoryService.createCategory(createRequest("Electronics", "electronics", null));
        CategoryResponse child = categoryService.createCategory(createRequest("Phones", "phones", root.id()));
        // Sibling root that must NOT appear in the subtree of "Electronics".
        categoryService.createCategory(createRequest("Books", "books", null));

        CategoryTreeResponse subtree = categoryService.getSubtree(root.id());

        assertThat(subtree.id()).isEqualTo(root.id());
        assertThat(subtree.children()).hasSize(1);
        assertThat(subtree.children().get(0).id()).isEqualTo(child.id());
    }

    // ---- direct children -------------------------------------------------

    @Test
    void shouldListDirectChildren_orderedByDisplayOrder() {
        CategoryResponse parent = categoryService.createCategory(createRequest("Electronics", "electronics", null));
        // Insert out of display order; expect ascending displayOrder ordering.
        categoryService.createCategory(createRequest("Laptops", "laptops", parent.id(), 20));
        categoryService.createCategory(createRequest("Phones", "phones", parent.id(), 10));

        List<CategoryResponse> children = categoryService.getDirectChildren(parent.id());

        assertThat(children).extracting(CategoryResponse::slug)
                .containsExactly("phones", "laptops");
        assertThat(children).extracting(CategoryResponse::displayOrder)
                .containsExactly(10, 20);
        assertThat(children).allSatisfy(c -> assertThat(c.parentId()).isEqualTo(parent.id()));
    }

    @Test
    void shouldReturnEmptyChildren_whenParentHasNone() {
        CategoryResponse parent = categoryService.createCategory(createRequest("Electronics", "electronics", null));

        assertThat(categoryService.getDirectChildren(parent.id())).isEmpty();
    }

    @Test
    void shouldThrowNotFound_whenGettingChildrenOfUnknownParent() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> categoryService.getDirectChildren(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    // ---- ancestors -------------------------------------------------------

    @Test
    void shouldReturnAncestors_orderedByDepth() {
        CategoryResponse root = categoryService.createCategory(createRequest("Electronics", "electronics", null));
        CategoryResponse child = categoryService.createCategory(createRequest("Phones", "phones", root.id()));
        CategoryResponse grandchild = categoryService.createCategory(createRequest("Smartphones", "smartphones", child.id()));

        var ancestors = categoryService.getAncestors(grandchild.id());

        assertThat(ancestors).extracting("id").containsExactly(root.id(), child.id());
        assertThat(ancestors).extracting("depth").containsExactly(0, 1);
    }

    @Test
    void shouldReturnEmptyAncestors_forRootCategory() {
        CategoryResponse root = categoryService.createCategory(createRequest("Electronics", "electronics", null));

        assertThat(categoryService.getAncestors(root.id())).isEmpty();
    }

    // ---- update ----------------------------------------------------------

    @Test
    void shouldUpdateNameSlugAndDisplayOrder() {
        CategoryResponse created = categoryService.createCategory(createRequest("Electronics", "electronics", null));

        UpdateCategoryRequest update = new UpdateCategoryRequest(
                "Consumer Electronics", "consumer-electronics", "updated desc",
                null, 7, true, null, null, null);

        CategoryResponse updated = categoryService.updateCategory(created.id(), update);

        assertThat(updated.name()).isEqualTo("Consumer Electronics");
        assertThat(updated.slug()).isEqualTo("consumer-electronics");
        assertThat(updated.description()).isEqualTo("updated desc");
        assertThat(updated.displayOrder()).isEqualTo(7);

        Category persisted = categoryRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Consumer Electronics");
        assertThat(persisted.getSlug()).isEqualTo("consumer-electronics");
        assertThat(persisted.getDisplayOrder()).isEqualTo(7);
        assertThat(categoryRepository.existsBySlug("electronics")).isFalse();
    }

    @Test
    void shouldRejectUpdate_whenSlugConflictsWithAnotherCategory() {
        categoryService.createCategory(createRequest("Electronics", "electronics", null));
        CategoryResponse books = categoryService.createCategory(createRequest("Books", "books", null));

        UpdateCategoryRequest update = new UpdateCategoryRequest(
                "Books", "electronics", null, null, null, true, null, null, null);

        assertThatThrownBy(() -> categoryService.updateCategory(books.id(), update))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("electronics");

        // The conflicting update must not have altered the Books slug.
        assertThat(categoryRepository.findById(books.id()).orElseThrow().getSlug()).isEqualTo("books");
    }

    @Test
    void shouldAllowUpdate_keepingOwnSlugUnchanged() {
        CategoryResponse created = categoryService.createCategory(createRequest("Electronics", "electronics", null));

        UpdateCategoryRequest update = new UpdateCategoryRequest(
                "Electronics Renamed", "electronics", null, null, 3, true, null, null, null);

        CategoryResponse updated = categoryService.updateCategory(created.id(), update);

        assertThat(updated.name()).isEqualTo("Electronics Renamed");
        assertThat(updated.slug()).isEqualTo("electronics");
        assertThat(updated.displayOrder()).isEqualTo(3);
    }

    @Test
    void shouldReparentCategory_updatingParentDepthAndDescendantPaths() {
        CategoryResponse rootA = categoryService.createCategory(createRequest("Electronics", "electronics", null));
        CategoryResponse rootB = categoryService.createCategory(createRequest("Books", "books", null));
        CategoryResponse child = categoryService.createCategory(createRequest("Phones", "phones", rootA.id()));
        CategoryResponse grandchild = categoryService.createCategory(createRequest("Smartphones", "smartphones", child.id()));

        // Move "Phones" (with its subtree) from Electronics to Books.
        UpdateCategoryRequest move = new UpdateCategoryRequest(
                "Phones", "phones", null, rootB.id(), null, true, null, null, null);

        CategoryResponse moved = categoryService.updateCategory(child.id(), move);

        assertThat(moved.parentId()).isEqualTo(rootB.id());
        assertThat(moved.depth()).isEqualTo(1);

        Category persistedChild = categoryRepository.findById(child.id()).orElseThrow();
        assertThat(persistedChild.getPath()).isEqualTo("/" + rootB.id() + "/" + child.id());

        // Descendant path was bulk-rewritten to sit under the new parent.
        Category persistedGrandchild = categoryRepository.findById(grandchild.id()).orElseThrow();
        assertThat(persistedGrandchild.getPath())
                .isEqualTo("/" + rootB.id() + "/" + child.id() + "/" + grandchild.id());
        assertThat(persistedGrandchild.getDepth()).isEqualTo(2);
    }

    @Test
    void shouldThrowNotFound_whenUpdatingUnknownCategory() {
        UUID unknown = UUID.randomUUID();
        UpdateCategoryRequest update = new UpdateCategoryRequest(
                "Nope", "nope", null, null, null, true, null, null, null);

        assertThatThrownBy(() -> categoryService.updateCategory(unknown, update))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    // ---- delete ----------------------------------------------------------

    @Test
    void shouldSoftDeleteCategory_makingItInactiveAndUnfetchable() {
        CategoryResponse created = categoryService.createCategory(createRequest("Electronics", "electronics", null));

        categoryService.deleteCategory(created.id());

        // Soft delete: row remains but is no longer "active".
        assertThat(categoryRepository.findActiveById(created.id())).isEmpty();
        Category raw = categoryRepository.findById(created.id()).orElseThrow();
        assertThat(raw.getDeletedAt()).isNotNull();

        assertThatThrownBy(() -> categoryService.getCategoryById(created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldRejectDelete_whenCategoryHasActiveChildren() {
        CategoryResponse parent = categoryService.createCategory(createRequest("Electronics", "electronics", null));
        categoryService.createCategory(createRequest("Phones", "phones", parent.id()));

        assertThatThrownBy(() -> categoryService.deleteCategory(parent.id()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("child categories");

        // Parent must still be active after the rejected delete.
        assertThat(categoryRepository.findActiveById(parent.id())).isPresent();
    }

    @Test
    void shouldThrowNotFound_whenDeletingUnknownCategory() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> categoryService.deleteCategory(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void shouldThrowNotFound_whenDeletingAlreadyDeletedCategory() {
        CategoryResponse created = categoryService.createCategory(createRequest("Electronics", "electronics", null));
        categoryService.deleteCategory(created.id());

        // A second delete sees no active row and is treated as not found.
        assertThatThrownBy(() -> categoryService.deleteCategory(created.id()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(created.id().toString());
    }

    @Test
    void shouldAllowReusingSlug_afterOriginalCategorySoftDeleted() {
        // existsBySlug only counts active rows, so a soft-deleted slug can be reused
        // by a brand new category. (Boundary of slug-uniqueness enforcement.)
        CategoryResponse first = categoryService.createCategory(createRequest("Electronics", "electronics", null));
        categoryService.deleteCategory(first.id());

        CategoryResponse second = categoryService.createCategory(createRequest("Electronics Redux", "electronics", null));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.slug()).isEqualTo("electronics");
    }
}
