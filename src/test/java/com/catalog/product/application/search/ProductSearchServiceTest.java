package com.catalog.product.application.search;

import com.catalog.attribute.infrastructure.AttributeValueRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.common.observability.metrics.SearchMetrics;
import com.catalog.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AttributeValueRepository attributeValueRepository;
    @Mock
    private SearchMetrics searchMetrics;

    @InjectMocks
    private ProductSearchService searchService;

    @Test
    void searchWithCategoryAttributesAndValidCursorQueriesAndRecordsMetrics() {
        UUID categoryId = UUID.randomUUID();
        Category category = org.mockito.Mockito.mock(Category.class);
        when(category.getId()).thenReturn(categoryId);
        when(category.getPath()).thenReturn("/electronics");
        when(categoryRepository.findActiveById(categoryId)).thenReturn(Optional.of(category));

        Set<UUID> attributeValueIds = Set.of(UUID.randomUUID());
        when(attributeValueRepository.findDistinctTypeIdsByValueIds(attributeValueIds))
                .thenReturn(Set.of(UUID.randomUUID()));

        List<ProductCardDto> rows = List.of(sampleCard());
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<ProductCardDto>>any())).thenReturn(rows);

        String cursor = SearchCursor.of(Instant.now(), "Zeta", BigDecimal.TEN, UUID.randomUUID()).encode();
        ProductFilterParams params = new ProductFilterParams(
                categoryId, UUID.randomUUID(), new BigDecimal("1.00"), new BigDecimal("9.00"),
                attributeValueIds, false, "phone", SortOption.PRICE_LOW, cursor, 20);

        CursorPage<ProductCardDto> page = searchService.search(params);

        assertThat(page.getContent()).hasSize(1);
        verify(categoryRepository).findActiveById(categoryId);
        // hasAttributeFilter flag must be true when attribute ids are present.
        verify(searchMetrics).recordSearch(anyLong(), eq(1), eq(true));
    }

    @Test
    void searchWithUnknownCategoryThrowsNotFoundAndRecordsErrorMetric() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findActiveById(categoryId)).thenReturn(Optional.empty());

        ProductFilterParams params = new ProductFilterParams(
                categoryId, null, null, null, Set.of(), null, null, SortOption.NEWEST, null, 20);

        assertThatThrownBy(() -> searchService.search(params))
                .isInstanceOf(ResourceNotFoundException.class);

        // The catch block must flag the failure before rethrowing.
        verify(searchMetrics).recordSearchError();
    }

    @Test
    void searchWithMalformedCursorIgnoresItAndRunsWithoutAttributeFilter() {
        List<ProductCardDto> rows = List.of(sampleCard(), sampleCard());
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<ProductCardDto>>any())).thenReturn(rows);

        ProductFilterParams params = new ProductFilterParams(
                null, null, null, null, Set.of(), null, "  ", SortOption.NEWEST, "!!!not-base64!!!", 20);

        CursorPage<ProductCardDto> page = searchService.search(params);

        // A bad cursor is treated as no cursor: the query still runs and returns results.
        assertThat(page.getContent()).hasSize(2);
        verify(searchMetrics).recordSearch(anyLong(), eq(2), eq(false));
    }

    private static ProductCardDto sampleCard() {
        return new ProductCardDto(
                UUID.randomUUID(), "Product", "product", "Short", ProductStatus.ACTIVE,
                null, null, null, null, BigDecimal.TEN, BigDecimal.TEN, true, 1, Instant.now());
    }
}
