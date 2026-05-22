→ [src/main/resources/application.yml] [156] lines — Spring Boot application configuration.
→ [src/main/resources/application-test.yml] [36] lines — Spring Boot test profile configuration.
→ [src/main/resources/application-local.yml] [12] lines — Spring Boot local profile configuration.
→ [src/main/resources/application-prod.yml] [28] lines — Spring Boot production profile configuration.
→ [src/main/resources/resilience4j.yml] [41] lines — Resilience4j circuit breaker/retry/rate-limiter configuration.
→ [src/main/resources/logback-spring.xml] [59] lines — Logback logging configuration for Spring Boot.
→ [pom.xml] [368] lines — XML configuration.
→ [src/main/resources/db/migration/V1__init_extensions.sql] [7] lines — Flyway migration `V1`.
→ [src/main/resources/db/migration/V2__create_categories.sql] [75] lines — Flyway migration `V2`.
→ [src/main/resources/db/migration/V3__create_brands.sql] [76] lines — Flyway migration `V3`.
→ [src/main/resources/db/migration/V4__create_products.sql] [128] lines — Flyway migration `V4`.
→ [src/main/resources/db/migration/V5__create_attributes_and_variants.sql] [191] lines — Flyway migration `V5`.
→ [src/main/resources/db/migration/V6__create_warehouses_and_inventory.sql] [98] lines — Flyway migration `V6`.
→ [src/main/resources/db/migration/V7__enhance_product_images_add_variant_images.sql] [101] lines — Flyway migration `V7`.
→ [src/main/resources/db/migration/V8__inventory_journal_and_bulk_jobs.sql] [139] lines — Flyway migration `V8`.
→ [src/main/resources/db/migration/V9__search_projection_and_performance.sql] [93] lines — Flyway migration `V9`.
→ [src/main/resources/db/migration/V10__hardening.sql] [22] lines — Flyway migration `V10`.
→ [src/main/resources/db/migration/V11__product_bulk_jobs.sql] [39] lines — Flyway migration `V11`.
→ [src/main/resources/db/migration/V12__orders_and_reservation_idempotency.sql] [72] lines — Flyway migration `V12`.
→ [src/main/resources/db/migration/V13__add_reservation_cleanup_index.sql] [3] lines — Flyway migration `V13`.
→ [src/main/resources/db/migration/V14__fix_table.sql] [4] lines — Flyway migration `V14`.
→ [src/main/resources/db/migration/V15__fix_bulk_table.sql] [2] lines — Flyway migration `V15`.
→ [src/main/java/com/catalog/common/security/RateLimitingFilter.java] [194] lines — Java type `RateLimitingFilter`.
→ [src/main/java/com/catalog/common/security/RedisTokenBucketRateLimiter.java] [82] lines — Java type `RedisTokenBucketRateLimiter`.
→ [src/main/java/com/catalog/common/security/IdempotencyFilter.java] [188] lines — Java type `IdempotencyFilter`.
→ [src/main/java/com/catalog/common/security/ApiKeyAuthFilter.java] [76] lines — Java type `ApiKeyAuthFilter`.
→ [src/main/java/com/catalog/common/security/SecurityHeadersFilter.java] [58] lines — Java type `SecurityHeadersFilter`.
→ [src/main/java/com/catalog/common/exception/GlobalExceptionHandler.java] [304] lines — Spring MVC controller `GlobalExceptionHandler`.
→ [src/main/java/com/catalog/common/exception/BusinessRuleViolationException.java] [8] lines — Exception type `BusinessRuleViolationException`.
→ [src/main/java/com/catalog/common/exception/DuplicateResourceException.java] [8] lines — Exception type `DuplicateResourceException`.
→ [src/main/java/com/catalog/common/exception/InsufficientStockException.java] [15] lines — Exception type `InsufficientStockException`.
→ [src/main/java/com/catalog/common/exception/ResourceNotFoundException.java] [8] lines — Exception type `ResourceNotFoundException`.
→ [src/main/java/com/catalog/common/exception/StorageUnavailableException.java] [8] lines — Exception type `StorageUnavailableException`.
→ [src/main/java/com/catalog/inventory/domain/Inventory.java] [182] lines — JPA entity `Inventory`.
→ [src/main/java/com/catalog/inventory/domain/InventoryJournal.java] [151] lines — JPA entity `is`.
→ [src/main/java/com/catalog/inventory/domain/InventoryReservation.java] [82] lines — JPA entity `InventoryReservation`.
→ [src/main/java/com/catalog/inventory/domain/BulkImportJob.java] [80] lines — JPA entity `BulkImportJob`.
→ [src/main/java/com/catalog/product/domain/Product.java] [166] lines — JPA entity `Product`.
→ [src/main/java/com/catalog/product/domain/BulkProductUpdateJob.java] [81] lines — JPA entity `BulkProductUpdateJob`.
→ [src/main/java/com/catalog/order/domain/Order.java] [64] lines — JPA entity `Order`.
→ [src/main/java/com/catalog/order/domain/OrderLineItem.java] [42] lines — JPA entity `OrderLineItem`.
→ [src/main/java/com/catalog/warehouse/domain/Warehouse.java] [50] lines — JPA entity `Warehouse`.
→ [src/main/java/com/catalog/inventory/application/InventoryService.java] [329] lines — Spring service `InventoryService`.
→ [src/main/java/com/catalog/inventory/application/InventoryTransferService.java] [139] lines — Spring service `InventoryTransferService`.
→ [src/main/java/com/catalog/inventory/application/BulkInventoryService.java] [244] lines — Spring service `BulkInventoryService`.
→ [src/main/java/com/catalog/inventory/application/BulkInventoryProcessor.java] [103] lines — Java type `BulkInventoryProcessor`.
→ [src/main/java/com/catalog/inventory/application/ReservationCleanupJob.java] [119] lines — Java type `ReservationCleanupJob`.
→ [src/main/java/com/catalog/product/application/ProductService.java] [261] lines — Spring service `ProductService`.
→ [src/main/java/com/catalog/product/application/ProductBulkUpdateService.java] [181] lines — Spring service `ProductBulkUpdateService`.
→ [src/main/java/com/catalog/product/application/search/ProductSearchService.java] [125] lines — Spring service `ProductSearchService`.
→ [src/main/java/com/catalog/product/application/search/ProductSearchProjectionService.java] [248] lines — Spring service `ProductSearchProjectionService`.
→ [src/main/java/com/catalog/product/application/search/ProductAdminQueryService.java] [69] lines — Spring service `ProductAdminQueryService`.
→ [src/main/java/com/catalog/product/application/search/ProductSearchCacheService.java] [149] lines — Spring service `ProductSearchCacheService`.
→ [src/main/java/com/catalog/order/application/OrderService.java] [74] lines — Spring service `OrderService`.
→ [src/main/java/com/catalog/warehouse/application/WarehouseService.java] [93] lines — Spring service `WarehouseService`.
→ [src/main/java/com/catalog/media/storage/S3StorageService.java] [101] lines — Spring service `S3StorageService`.
→ [src/main/java/com/catalog/media/storage/StorageService.java] [25] lines — Java type `StorageService`.
→ [src/main/java/com/catalog/media/product/application/ProductImageService.java] [206] lines — Spring service `ProductImageService`.
→ [src/main/java/com/catalog/media/product/application/ImageProcessingService.java] [60] lines — Spring service `ImageProcessingService`.
→ [src/main/java/com/catalog/common/cache/CacheEvictionService.java] [87] lines — Spring service `CacheEvictionService`.
→ [src/main/java/com/catalog/attribute/application/AttributeService.java] [133] lines — Spring service `AttributeService`.
→ [src/main/java/com/catalog/brand/application/BrandService.java] [225] lines — Spring service `BrandService`.
→ [src/main/java/com/catalog/category/application/CategoryService.java] [288] lines — Spring service `CategoryService`.
→ [src/main/java/com/catalog/variant/application/VariantService.java] [283] lines — Spring service `VariantService`.
→ [src/main/java/com/catalog/inventory/api/InventoryController.java] [111] lines — Spring MVC controller `InventoryController`.
→ [src/main/java/com/catalog/inventory/api/BulkImportController.java] [54] lines — Spring MVC controller `BulkImportController`.
→ [src/main/java/com/catalog/product/api/ProductController.java] [159] lines — Spring MVC controller `ProductController`.
→ [src/main/java/com/catalog/brand/api/BrandController.java] [100] lines — Spring MVC controller `BrandController`.
→ [src/main/java/com/catalog/category/api/CategoryController.java] [100] lines — Spring MVC controller `CategoryController`.
→ [src/main/java/com/catalog/attribute/api/AttributeController.java] [59] lines — Spring MVC controller `AttributeController`.
→ [src/main/java/com/catalog/variant/api/VariantController.java] [84] lines — Spring MVC controller `VariantController`.
→ [src/main/java/com/catalog/warehouse/api/WarehouseController.java] [53] lines — Spring MVC controller `WarehouseController`.
→ [src/main/java/com/catalog/inventory/infrastructure/InventoryRepository.java] [53] lines — Java type `InventoryRepository`.
→ [src/main/java/com/catalog/inventory/infrastructure/InventoryJournalRepository.java] [50] lines — Java type `InventoryJournalRepository`.
→ [src/main/java/com/catalog/inventory/infrastructure/InventoryReservationRepository.java] [34] lines — Java type `InventoryReservationRepository`.
→ [src/main/java/com/catalog/inventory/infrastructure/BulkImportJobRepository.java] [12] lines — Java type `BulkImportJobRepository`.
→ [src/main/java/com/catalog/product/infrastructure/ProductRepository.java] [88] lines — Java type `ProductRepository`.
→ [src/test/java/com/catalog/common/BaseIntegrationTest.java] [18] lines — Spring service `BaseIntegrationTest`.
→ [src/test/java/com/catalog/inventory/application/InventoryConcurrencyIT.java] [129] lines — Java type `InventoryConcurrencyIT`.
→ [src/test/java/com/catalog/inventory/domain/InventoryDomainTest.java] [66] lines — Java type `InventoryDomainTest`.
→ [src/test/java/com/catalog/common/security/RateLimitingFilterTest.java] [63] lines — Java type `RateLimitingFilterTest`.
→ [src/test/java/com/catalog/common/security/IdempotencyFilterTest.java] [56] lines — Java type `IdempotencyFilterTest`.
→ [src/test/java/com/catalog/product/application/ProductServiceTest.java] [97] lines — Java type `ProductServiceTest`.
→ [src/main/java/com/catalog/common/observability/health/DatabasePoolHealthIndicator.java] [84] lines — Spring Boot health indicator `DatabasePoolHealthIndicator`.
→ [src/main/java/com/catalog/common/observability/health/RedisHealthIndicator.java] [55] lines — Spring Boot health indicator `RedisHealthIndicator`.
→ [src/main/java/com/catalog/common/observability/metrics/InventoryMetrics.java] [85] lines — Java type `InventoryMetrics`.
→ [src/main/java/com/catalog/common/response/ErrorResponse.java] [23] lines — Java type `ErrorResponse`.
→ [src/main/java/com/catalog/common/response/ApiResponse.java] [34] lines — Java type `ApiResponse`.
