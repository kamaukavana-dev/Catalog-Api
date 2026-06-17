package com.catalog.product.application.search;

import com.catalog.common.BaseIntegrationTest;
import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.inventory.domain.Inventory;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.product.domain.Product;
import com.catalog.product.infrastructure.ProductRepository;
import com.catalog.variant.domain.Variant;
import com.catalog.variant.domain.TaxClass;
import com.catalog.variant.infrastructure.VariantRepository;
import com.catalog.warehouse.domain.Warehouse;
import com.catalog.warehouse.domain.WarehouseType;
import com.catalog.warehouse.infrastructure.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ProductSearchProjectionServiceIT extends BaseIntegrationTest {

    @Autowired
    private ProductSearchProjectionService projectionService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Product product;
    private Brand brand;
    private Category category;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM product_search_projection");

        brand = brandRepository.save(Brand.create("Brand A", "brand-a-" + UUID.randomUUID(), "Desc"));
        
        // Need to check Category.createRoot exact signature
        category = categoryRepository.save(Category.createRoot("Category A", "cat-a-" + UUID.randomUUID(), "Desc"));
        
        product = Product.createDraft("Test Product", "test-product-" + UUID.randomUUID());
        product.assignBrand(brand);
        product.assignPrimaryCategory(category);
        product = productRepository.save(product);
    }

    @Test
    void shouldCreateProjection_whenProductIsCreated() {
        projectionService.refreshProjection(product.getId());

        Map<String, Object> projection = jdbcTemplate.queryForMap(
                "SELECT * FROM product_search_projection WHERE product_id = ?", product.getId());
        
        assertThat(projection.get("name")).isEqualTo("Test Product");
        assertThat(projection.get("brand_name")).isEqualTo("Brand A");
        assertThat(projection.get("primary_category_name")).isEqualTo("Category A");
    }

    @Test
    void shouldUpdateProjection_whenProductNameChanges() {
        projectionService.refreshProjection(product.getId());

        product.updateName("Updated Product", "updated-product-" + UUID.randomUUID());
        productRepository.save(product);

        projectionService.refreshProjection(product.getId());

        String name = jdbcTemplate.queryForObject(
                "SELECT name FROM product_search_projection WHERE product_id = ?", String.class, product.getId());
        assertThat(name).isEqualTo("Updated Product");
    }

    @Test
    void shouldReflectInventoryStatus_inProjection() {
        Variant variant = Variant.createDraft(product, "SKU-PROJ-" + UUID.randomUUID(), new BigDecimal("100.00"), TaxClass.STANDARD);
        variant.setStatus(com.catalog.variant.domain.VariantStatus.ACTIVE);
        variant = variantRepository.save(variant);
        
        Warehouse warehouse = warehouseRepository.save(Warehouse.create("WH-PROJ", "Proj WH", WarehouseType.MAIN));
        Inventory inventory = Inventory.create(variant, warehouse, 5);
        inventory.receiveStock(10);
        inventoryRepository.save(inventory);

        projectionService.refreshProjection(product.getId());

        Boolean inStock = jdbcTemplate.queryForObject(
                "SELECT in_stock FROM product_search_projection WHERE product_id = ?", Boolean.class, product.getId());
        assertThat(inStock).isTrue();
        
        // in_stock_variant_count is an integer in DB
        Number inStockCount = jdbcTemplate.queryForObject(
                "SELECT in_stock_variant_count FROM product_search_projection WHERE product_id = ?", Number.class, product.getId());
        assertThat(inStockCount.intValue()).isEqualTo(1);
    }
}
