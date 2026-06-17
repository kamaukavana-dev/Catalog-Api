package com.catalog.brand.infrastructure;

import com.catalog.brand.domain.Brand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for BrandRepository against real PostgreSQL via Testcontainers.
 * Flyway migrations run against the container; the embedded-DB replacement is disabled.
 */
@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BrandRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");


    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BrandRepository brandRepository;

    @Test
    void shouldPersistAndFindActiveBrandById() {
        Brand brand = Brand.create("Nike", "nike", "Just do it");
        entityManager.persistAndFlush(brand);

        Optional<Brand> found = brandRepository.findActiveById(brand.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Nike");
    }

    @Test
    void shouldNotFindSoftDeletedBrand() {
        Brand brand = Brand.create("Deleted Brand", "deleted-brand", null);
        entityManager.persistAndFlush(brand);
        brand.markDeleted();
        entityManager.persistAndFlush(brand);

        assertThat(brandRepository.findActiveById(brand.getId())).isEmpty();
        assertThat(brandRepository.findActiveBySlug("deleted-brand")).isEmpty();
    }

    @Test
    void shouldAllowSlugReuseAfterSoftDelete() {
        Brand brand = Brand.create("Old Nike", "nike", null);
        entityManager.persistAndFlush(brand);
        brand.markDeleted();
        entityManager.persistAndFlush(brand);

        Brand reborn = Brand.create("New Nike", "nike", null);
        entityManager.persistAndFlush(reborn);

        assertThat(reborn.getSlug()).isEqualTo("nike");
    }

    @Test
    void filterQueryShouldReturnPagedResults() {
        brandRepository.save(Brand.create("Adidas", "adidas", null));
        brandRepository.save(Brand.create("Nike", "nike", null));
        brandRepository.save(Brand.create("Puma", "puma", null));

        Page<Brand> result = brandRepository.findByFilters(
            "ni", null, null, null, PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Nike");
    }

    @Test
    void shouldEnforceNameUniquenessAmongActiveRecords() {
        brandRepository.save(Brand.create("Samsung", "samsung", null));
        assertThat(brandRepository.existsByName("Samsung")).isTrue();
        assertThat(brandRepository.existsByName("samsung")).isTrue();
    }
}

