package com.catalog.product.domain;

import com.catalog.brand.domain.Brand;
import com.catalog.category.domain.Category;
import com.catalog.common.audit.BaseEntity;
import com.catalog.common.exception.BusinessRuleViolationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "products", uniqueConstraints = {
    @UniqueConstraint(name = "uidx_product_slug", columnNames = {"slug"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Column(name = "slug", nullable = false, length = 300)
    private String slug;

    @Column(name = "short_description", length = 1000)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_category_id")
    private Category primaryCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> secondaryCategories = new HashSet<>();

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @Column(name = "meta_title", length = 200)
    private String metaTitle;

    @Column(name = "meta_description", length = 500)
    private String metaDescription;

    public static Product createDraft(String name, String slug) {
        Product product = new Product();
        product.name = name;
        product.slug = slug;
        product.status = ProductStatus.DRAFT;
        return product;
    }

    public void updateName(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public void updateContent(String shortDescription, String description, String metaTitle, String metaDescription) {
        this.shortDescription = shortDescription;
        this.description = description;
        this.metaTitle = metaTitle;
        this.metaDescription = metaDescription;
    }

    public void assignPrimaryCategory(Category category) {
        this.primaryCategory = category;
    }

    public void assignBrand(Brand brand) {
        this.brand = brand;
    }

    public void transitionTo(ProductStatus target) {
        this.status.assertCanTransitionTo(target);

        if (target == ProductStatus.ACTIVE) {
            assertActivationPreconditions();
        }

        this.status = target;
    }

    private void assertActivationPreconditions() {
        if (this.primaryCategory == null) {
            throw new BusinessRuleViolationException(
                    "Cannot activate product '" + this.name + "': primary category is required for active products."
            );
        }
        if (this.name == null || this.name.isBlank()) {
            throw new BusinessRuleViolationException("Cannot activate product: name is required.");
        }
    }

    public void addSecondaryCategory(Category category) {
        if (this.primaryCategory != null && this.primaryCategory.getId().equals(category.getId())) {
            throw new BusinessRuleViolationException(
                    "Category '" + category.getName() + "' is already set as the primary category. " +
                            "A category cannot be both primary and secondary."
            );
        }
        this.secondaryCategories.add(category);
    }

    public void removeSecondaryCategory(Category category) {
        this.secondaryCategories.remove(category);
    }

    public boolean isDeletable() {
        return this.status == ProductStatus.DRAFT
                || this.status == ProductStatus.INACTIVE
                || this.status == ProductStatus.ARCHIVED;
    }

    public boolean isVisible() {
        return this.status == ProductStatus.ACTIVE && !isDeleted();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(slug, product.slug);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug);
    }
}
