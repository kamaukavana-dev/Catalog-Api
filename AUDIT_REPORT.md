# Audit Report (May 18, 2026)

This report summarizes findings and fixes based on the current codebase state.

## Scope
- Security, data handling, resilience, observability, API behavior, and documentation alignment.

## Findings (ordered by severity)

### Medium
1. CSV uploads rejected valid content types with charset parameters.
   - Root cause: strict equality check on `MultipartFile#getContentType`.
   - Fix: normalize content type before validation.
   - Evidence: `src/main/java/com/catalog/product/application/ProductBulkUpdateService.java`,
     `src/main/java/com/catalog/inventory/application/BulkInventoryService.java`.

2. Bulk inventory CSV reader was not closed.
   - Root cause: missing try-with-resources around the CSV reader.
   - Fix: wrap reader in try-with-resources.
   - Evidence: `src/main/java/com/catalog/inventory/application/BulkInventoryService.java`.

### Low
3. API and security docs drifted from actual behavior.
   - Root cause: documentation not updated after API key guard and bulk endpoint changes.
   - Fix: update `API.md` and `SECURITY.md`.

4. README local setup text out of date (broken DB password line).
   - Root cause: documentation drift.
   - Fix: update `README.md`.

## Fixes Applied
- Normalized CSV content-type validation for bulk product and inventory uploads.
- Closed CSV reader in bulk inventory import.
- Added test coverage for `text/csv; charset=utf-8` uploads.
- Updated `README.md`, `API.md`, `SECURITY.md`, `DEVELOPMENT.md`, `DEPLOYMENT.md` to match verified behavior.

## Tests
- `mvn test` (all tests).

## Notes
- A MapStruct processor warning was observed during compilation. No functional impact confirmed yet.

