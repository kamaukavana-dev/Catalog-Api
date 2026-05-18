# API Documentation

This document provides details on the available API endpoints.

## Conventions

- **Base URL**: All API endpoints are prefixed with `/api/v1`.
- **Authentication**: Optional API key guard for mutation endpoints. When enabled, send `X-Api-Key`.
- **Error Responses**: Errors are returned in a standardized JSON format:
  ```json
  {
    "timestamp": "2023-10-27T10:00:00.000+00:00",
    "status": 400,
    "error": "Bad Request",
    "message": "Validation error message",
    "path": "/api/v1/products"
  }
  ```

## Endpoints

### Products

#### `POST /api/v1/products/bulk-update`

Asynchronously updates products in bulk from a CSV file.

- **Request**:
  - `Content-Type`: `multipart/form-data`
  - `file`: The CSV file to upload.
  - `importSessionId`: UUID used for idempotent job submission.
- **CSV Format**:
  - The CSV file must have a header row.
  - Required columns: `product_id`, `name`.
- **Responses**:
  - `202 Accepted`: On successful submission of the file. The processing is done asynchronously.
  - `400 Bad Request`: If the file is empty, not a CSV, or malformed.

**Evidence**:
- `com.catalog.product.api.ProductController#bulkUpdateProducts`
- `com.catalog.product.application.ProductBulkUpdateService#parseCSV`

---

### Inventory

#### `POST /api/v1/inventory/bulk-imports`

Asynchronously imports inventory adjustments from a CSV file.

- **Request**:
  - `Content-Type`: `multipart/form-data`
  - `file`: The CSV file to upload.
  - `importSessionId`: UUID used for idempotent job submission.
- **CSV Format**:
  - The CSV file must have a header row.
  - Required columns: `variant_sku`, `warehouse_code`, `adjustment_type`, `quantity`, `reason`.
- **Responses**:
  - `202 Accepted`: On successful submission of the file. The processing is done asynchronously.
  - `400 Bad Request`: If the file is empty, not a CSV, or malformed.

**Evidence**:
- `com.catalog.inventory.api.BulkImportController#submitImport`
- `com.catalog.inventory.application.BulkInventoryService#parseCSV`

---

*Note: This document is based on the current state of the codebase. Other standard CRUD endpoints for Products, Categories, and Brands are implied by the service and repository layers but are not explicitly exposed in the controllers at this time. Their implementation is NOT VERIFIED FROM CODEBASE.*
