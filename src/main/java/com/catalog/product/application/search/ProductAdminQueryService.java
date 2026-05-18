package com.catalog.product.application.search;

import com.catalog.product.domain.Product;
import com.catalog.product.domain.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAdminQueryService {

    private final JPAQueryFactory queryFactory;

    @Transactional(readOnly = true)
    public Page<Product> adminList(AdminProductFilterParams params, Pageable pageable) {
        QProduct product = QProduct.product;
        BooleanBuilder predicate = new BooleanBuilder();
        predicate.and(product.deletedAt.isNull());

        if (params.statuses() != null && !params.statuses().isEmpty()) {
            predicate.and(product.status.in(params.statuses()));
        }
        if (params.brandId() != null) {
            predicate.and(product.brand.id.eq(params.brandId()));
        }
        if (params.categoryId() != null) {
            predicate.and(product.primaryCategory.id.eq(params.categoryId()));
        }
        if (params.search() != null && !params.search().isBlank()) {
            predicate.and(product.name.containsIgnoreCase(params.search().trim()));
        }

        OrderSpecifier<?>[] orderSpecifiers = switch (params.sort()) {
            case OLDEST -> new OrderSpecifier[]{new OrderSpecifier<>(Order.ASC, product.createdAt), new OrderSpecifier<>(Order.ASC, product.id)};
            case NAME_ASC -> new OrderSpecifier[]{new OrderSpecifier<>(Order.ASC, product.name), new OrderSpecifier<>(Order.ASC, product.id)};
            case NAME_DESC -> new OrderSpecifier[]{new OrderSpecifier<>(Order.DESC, product.name), new OrderSpecifier<>(Order.ASC, product.id)};
            default -> new OrderSpecifier[]{new OrderSpecifier<>(Order.DESC, product.createdAt), new OrderSpecifier<>(Order.DESC, product.id)};
        };

        List<Product> results = queryFactory
                .selectFrom(product)
                .leftJoin(product.brand).fetchJoin()
                .leftJoin(product.primaryCategory).fetchJoin()
                .where(predicate)
                .orderBy(orderSpecifiers)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .where(predicate)
                .fetchOne();

        return new PageImpl<>(results, pageable, total == null ? 0L : total);
    }
}

