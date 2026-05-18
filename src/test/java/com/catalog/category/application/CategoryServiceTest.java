package com.catalog.category.application;

import com.catalog.category.api.dto.request.CreateCategoryRequest;
import com.catalog.category.api.mapper.CategoryMapper;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void shouldThrowWhenSlugAlreadyExists() {
        when(categoryRepository.existsBySlug("electronics")).thenReturn(true);

        CreateCategoryRequest request = new CreateCategoryRequest(
            "Electronics", "electronics", null, null, 0, null, null, null
        );

        assertThatThrownBy(() -> categoryService.createCategory(request))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("electronics");
    }

    @Test
    void shouldThrowWhenDeletingCategoryWithChildren() {
        UUID categoryId = UUID.randomUUID();
        Category category = Category.createRoot("Electronics", "electronics", null);

        when(categoryRepository.findActiveById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.hasActiveChildren(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(categoryId))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("child categories");
    }
}

