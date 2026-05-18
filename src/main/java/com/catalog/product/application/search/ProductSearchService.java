package com.catalog.product.application.search;

import com.catalog.attribute.infrastructure.AttributeValueRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.product.domain.ProductStatus;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CategoryRepository categoryRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final com.catalog.common.observability.metrics.SearchMetrics searchMetrics;

    @Observed(
        name = "catalog.search",
        contextualName = "product-search",
        lowCardinalityKeyValues = {"component", "search"}
    )
    @Transactional(readOnly = true)
    public CursorPage<ProductCardDto> search(ProductFilterParams params) {
        long start = System.currentTimeMillis();
        try {
            SearchCursor cursor = decodeCursor(params.cursor());
            int numAttributeTypes = resolveAttributeTypeCount(params.attributeValueIds());

            ProductSearchQueryBuilder builder = new ProductSearchQueryBuilder()
                    .sort(params.sort())
                    .limit(params.pageSize())
                    .textSearch(params.search())
                    .brandFilter(params.brandId())
                    .priceFilter(params.minPrice(), params.maxPrice())
                    .inStockFilter(params.inStock())
                    .attributeFilter(params.attributeValueIds(), numAttributeTypes)
                    .cursorCondition(cursor, params.sort());

            if (params.categoryId() != null) {
                Category category = categoryRepository.findActiveById(params.categoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category", params.categoryId()));
                builder.categoryFilter(category.getId(), category.getPath());
            }

            ProductSearchQueryBuilder.SearchSql query = builder.build();
            List<ProductCardDto> results = jdbcTemplate.query(query.sql(), query.params(), this::mapRow);

            CursorPage<ProductCardDto> result = CursorPage.of(
                results,
                params.pageSize(),
                dto -> SearchCursor.of(dto.createdAt(), dto.name(), dto.minEffectivePrice(), dto.id()).encode()
            );
            searchMetrics.recordSearch(
                System.currentTimeMillis() - start,
                result.getContent().size(),
                params.attributeValueIds() != null && !params.attributeValueIds().isEmpty()
            );
            return result;
        } catch (Exception e) {
            searchMetrics.recordSearchError();
            throw e;
        }
    }

    private int resolveAttributeTypeCount(Set<UUID> attributeValueIds) {
        if (attributeValueIds == null || attributeValueIds.isEmpty()) {
            return 0;
        }
        return attributeValueRepository.findDistinctTypeIdsByValueIds(attributeValueIds).size();
    }

    private SearchCursor decodeCursor(String cursorToken) {
        if (cursorToken == null || cursorToken.isBlank()) {
            return null;
        }
        try {
            return SearchCursor.decode(cursorToken);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid cursor token received: {}", ex.getMessage());
            return null;
        }
    }

    private ProductCardDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ProductCardDto(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("short_description"),
                ProductStatus.valueOf(rs.getString("status")),
                toUuid(rs.getString("primary_category_id")),
                rs.getString("category_name"),
                toUuid(rs.getString("brand_id")),
                rs.getString("brand_name"),
                rs.getBigDecimal("min_effective_price"),
                rs.getBigDecimal("max_base_price"),
                rs.getBoolean("in_stock"),
                rs.getInt("variant_count"),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    private UUID toUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private java.time.Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
