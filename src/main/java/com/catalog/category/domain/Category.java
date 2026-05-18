package com.catalog.category.domain;

import com.catalog.common.audit.BaseEntity;
import com.catalog.common.exception.BusinessRuleViolationException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "categories", uniqueConstraints = {
    @UniqueConstraint(name = "uidx_category_slug", columnNames = {"slug"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<Category> children = new ArrayList<>();

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "path", nullable = false, length = 2000)
    private String path;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "meta_title", length = 200)
    private String metaTitle;

    @Column(name = "meta_description", length = 500)
    private String metaDescription;

    // Factory method for root categories
    public static Category createRoot(String name, String slug, String description) {
        Category category = new Category();
        category.name = name;
        category.slug = slug;
        category.description = description;
        category.depth = 0;
        category.active = true;
        category.displayOrder = 0;
        category.path = "PENDING";
        return category;
    }

    // Factory method for child categories
    public static Category createChild(String name, String slug, String description,
                                       Category parent) {
        if (parent.getDepth() >= 10) {
            throw new BusinessRuleViolationException(
                "Maximum category depth of 10 exceeded."
            );
        }
        Category category = new Category();
        category.name = name;
        category.slug = slug;
        category.description = description;
        category.parent = parent;
        category.depth = parent.getDepth() + 1;
        category.active = true;
        category.displayOrder = 0;
        category.path = "PENDING";
        return category;
    }

    // Called after entity has persisted ID
    public void initializePath() {
        if (this.parent == null) {
            this.path = "/" + this.getId().toString();
        } else {
            this.path = this.parent.getPath() + "/" + this.getId().toString();
        }
    }

    public void updateDetails(String name,
                              String slug,
                              String description,
                              Integer displayOrder,
                              Boolean active,
                              String imageUrl,
                              String metaTitle,
                              String metaDescription) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
        if (active != null) {
            this.active = active;
        }
        this.imageUrl = imageUrl;
        this.metaTitle = metaTitle;
        this.metaDescription = metaDescription;
    }

    public void moveTo(Category newParent) {
        if (newParent == null) {
            this.parent = null;
            this.depth = 0;
            return;
        }

        if (this.getId() != null && newParent.getPath() != null
                && newParent.getPath().contains("/" + this.getId())) {
            throw new BusinessRuleViolationException(
                    "Cannot move category: target parent is a descendant of this category."
            );
        }

        int newDepth = newParent.getDepth() + 1;
        if (newDepth > 10) {
            throw new BusinessRuleViolationException(
                    "Cannot move category: would exceed maximum tree depth of 10."
            );
        }

        this.parent = newParent;
        this.depth = newDepth;
    }

    public boolean isRoot() {
        return this.parent == null;
    }

    public boolean hasChildren() {
        return !this.children.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return Objects.equals(slug, category.slug);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug);
    }
}
