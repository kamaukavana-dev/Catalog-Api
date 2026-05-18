package com.catalog.product.domain;

/**
 * Computed availability state. NEVER stored in DB — always derived.
 * Derived from: ProductStatus + VariantStatus + inventory.available_quantity
 *
 * Separation of concerns:
 * - ProductStatus: catalog lifecycle (DRAFT → ACTIVE → ARCHIVED)
 * - ProductAvailability: runtime purchasability (derived from inventory)
 *
 * An ACTIVE product CAN be OUT_OF_STOCK. These are independent dimensions.
 */
public enum ProductAvailability {
    IN_STOCK,     // Has at least one variant with available_quantity > reorder_level
    LOW_STOCK,    // Has stock but available_quantity <= reorder_level on all stocked variants
    OUT_OF_STOCK, // All active variants have available_quantity <= 0
    UNAVAILABLE   // Product is not ACTIVE or has no active variants
}

