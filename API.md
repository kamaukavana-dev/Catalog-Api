# API Documentation

> This is a partial hand-maintained summary. The **authoritative endpoint list** (read
> from the controllers) lives in [`README.md` → API overview](README.md#api-overview).

## Authentication
Mutation endpoints (POST, PUT, PATCH, DELETE) require an API key in the `X-Api-Key` header if configured.

## Common Headers
- `X-Idempotency-Key`: Required for all mutation endpoints to prevent duplicate processing.

## Endpoints

### Inventory
#### POST /api/v1/inventory
**Description**: Create a new inventory record for a variant at a warehouse.
**Status**: 201 Created

#### POST /api/v1/inventory/transfers
**Description**: Move stock between warehouses.
**Status**: 201 Created

#### PATCH /api/v1/inventory/{id}/stock
**Description**: Manually adjust stock levels (RECEIVE, RECONCILE).
**Status**: 200 OK

### Products
#### GET /api/v1/products/search
**Description**: Cursor-paginated search over the denormalized `product_search_projection` (GIN `tsvector` + `pg_trgm`).
**Parameters**: `search`, `brandId`, `categoryId`, `minPrice`, `maxPrice`, `inStock`, `sort`, `cursor`, `pageSize`.
**Status**: 200 OK

#### POST /api/v1/products
**Description**: Create a new product.
**Status**: 201 Created

### Categories
#### GET /api/v1/categories/tree
**Description**: Fetch the full hierarchical category tree.
**Status**: 200 OK
