package com.catalog.category.infrastructure;

import com.catalog.category.domain.Category;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CategoryRepository.
 * Requires a running database (PostgreSQL via Testcontainers).
 * Enable with @Disabled removed and proper database setup.
 */
@Disabled("Requires database setup via Testcontainers")
@DataJpaTest
class CategoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void shouldFindActiveCategoryBySlug() {
        Category cat = Category.createRoot("Electronics", "electronics", null);
        entityManager.persistAndFlush(cat);
        cat.initializePath();
        entityManager.persistAndFlush(cat);

        Optional<Category> found = categoryRepository.findActiveBySlug("electronics");
        assertThat(found).isPresent();
        assertThat(found.get().getSlug()).isEqualTo("electronics");
    }

    @Test
    void shouldNotFindDeletedCategoryBySlug() {
        Category cat = Category.createRoot("Deleted", "deleted-cat", null);
        entityManager.persistAndFlush(cat);
        cat.initializePath();
        entityManager.persistAndFlush(cat);

        cat.markDeleted();
        entityManager.persistAndFlush(cat);

        Optional<Category> found = categoryRepository.findActiveBySlug("deleted-cat");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldDetectExistingSlug() {
        Category cat = Category.createRoot("Test", "test-slug", null);
        entityManager.persistAndFlush(cat);
        cat.initializePath();
        entityManager.persistAndFlush(cat);

        assertThat(categoryRepository.existsBySlug("test-slug")).isTrue();
    }

    @Test
    void shouldFindActiveChildren() {
        Category parent = Category.createRoot("Parent", "parent", null);
        entityManager.persistAndFlush(parent);
        parent.initializePath();
        entityManager.persistAndFlush(parent);

        Category child = Category.createChild("Child", "child", null, parent);
        entityManager.persistAndFlush(child);
        child.initializePath();
        entityManager.persistAndFlush(child);

        List<Category> children = categoryRepository.findActiveChildrenByParentId(parent.getId());
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getSlug()).isEqualTo("child");
    }

    @Test
    void subtreeQueryShouldReturnAllDescendants() {
        Category root = Category.createRoot("Root", "root", null);
        entityManager.persistAndFlush(root);
        root.initializePath();
        entityManager.persistAndFlush(root);

        Category child = Category.createChild("Child", "child", null, root);
        entityManager.persistAndFlush(child);
        child.initializePath();
        entityManager.persistAndFlush(child);

        Category grandchild = Category.createChild("Grandchild", "grandchild", null, child);
        entityManager.persistAndFlush(grandchild);
        grandchild.initializePath();
        entityManager.persistAndFlush(grandchild);

        String pathPrefix = root.getPath() + "/";
        List<Category> subtree = categoryRepository.findActiveSubtree(pathPrefix);

        assertThat(subtree).hasSize(2); // child + grandchild, not root itself
    }
}



