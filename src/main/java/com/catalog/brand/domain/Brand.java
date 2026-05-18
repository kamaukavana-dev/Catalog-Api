package com.catalog.brand.domain;

import com.catalog.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

@Entity
@Table(name = "brands", uniqueConstraints = {
    @UniqueConstraint(name = "uidx_brand_slug", columnNames = {"slug"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "website_url", length = 1000)
    private String websiteUrl;

    @Column(name = "country_of_origin", length = 100)
    private String countryOfOrigin;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    public static Brand create(String name, String slug, String description) {
        Brand brand = new Brand();
        brand.name = name;
        brand.slug = slug;
        brand.description = description;
        brand.active = true;
        brand.featured = false;
        return brand;
    }

    public void updateIdentity(String name, String slug, String description) {
        this.name = name;
        this.slug = slug;
        this.description = description;
    }

    public void updateLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public void updateWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public void updateCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }

    public void updateFoundedYear(Integer foundedYear) {
        this.foundedYear = foundedYear;
    }

    public void updateActive(boolean active) {
        this.active = active;
    }

    public void updateFeatured(boolean featured) {
        this.featured = featured;
    }

    public boolean isSlugMutable() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Brand brand = (Brand) o;
        return Objects.equals(slug, brand.slug);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug);
    }
}
