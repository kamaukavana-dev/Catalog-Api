# Change Summary

## Code and Configuration
- Normalized CSV content-type validation for bulk product and inventory uploads.
- Closed CSV reader in bulk inventory import.
- Added test coverage for `text/csv; charset=utf-8` in bulk product updates.
- Added test annotation processor paths to remove MapStruct compiler-arg warnings during test compilation.

## Documentation
- Updated local setup and security notes in `README.md`.
- Updated `API.md` with correct bulk endpoints, CSV headers, and response codes.
- Updated `SECURITY.md` to reflect API key guard, rate limiting filter, and file validation.
- Updated `DEVELOPMENT.md` and `DEPLOYMENT.md` to align with current configuration.

## Tests
- Full test suite executed: 53 tests, 0 failures, 0 errors, 10 skipped.

