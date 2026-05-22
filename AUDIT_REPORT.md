# Catalog-API Audit Report (Phase 2)

This audit is based only on files read in Phase 1 (see `PHASE1_READ_LOG.md`). Each finding includes a concrete `file:line` reference.

[CRITICAL] [DATA_INTEGRITY] src/main/resources/db/migration/V15__fix_bulk_table.sql:1 — Migration V15 is a no-op with an inaccurate comment claiming V14 changed `bulk_import_jobs`, but V14 actually alters `product_bulk_jobs` — Operators may assume `bulk_import_jobs` has audit columns that do not exist, and future migrations may be planned on a false schema baseline.

[HIGH] [SECURITY] src/main/java/com/catalog/common/security/IdempotencyFilter.java:55-65 — Idempotency is optional (requests without `X-Idempotency-Key` are allowed to proceed) — Client retries can create duplicate resources / double-apply mutations when Redis is available or unavailable.

[HIGH] [SECURITY] src/main/java/com/catalog/common/security/IdempotencyFilter.java:32-34 — The filter explicitly excludes PUT/PATCH from idempotency enforcement based on REST semantics — Any non-idempotent PATCH/PUT endpoint is exposed to replay/double-apply risk.

[HIGH] [API_CONTRACT] src/main/java/com/catalog/common/exception/GlobalExceptionHandler.java:136-145 — Bean-validation errors return HTTP 400 with `"Validation Failed"` — Clients cannot distinguish syntactic request errors from semantic validation errors; contract requirements for 422 are not met.

[HIGH] [API_CONTRACT] src/main/java/com/catalog/common/exception/GlobalExceptionHandler.java:161-169 — Constraint violation errors return HTTP 400 with `"Validation Failed"` — Same contract issue as above; callers receive 400 instead of 422 for validation failures.

[HIGH] [API_CONTRACT] src/main/java/com/catalog/common/security/ApiKeyAuthFilter.java:55-61 — Authentication failures return a raw JSON string instead of the `ErrorResponse` envelope — Error shape is inconsistent across the API and bypasses `GlobalExceptionHandler` conventions.

[HIGH] [API_CONTRACT] src/main/java/com/catalog/common/security/RateLimitingFilter.java:90-96 — Rate limit failures return a raw JSON string instead of the `ErrorResponse` envelope — Error envelope inconsistency; clients cannot reliably parse errors across endpoints.

[HIGH] [API_CONTRACT] src/main/java/com/catalog/common/security/IdempotencyFilter.java:154-161 — Idempotency conflict response is a raw JSON string and does not include `path`/`timestamp`/`validationErrors` envelope fields — Contract inconsistency and reduced debuggability for clients.

[HIGH] [API_CONTRACT] src/main/java/com/catalog/inventory/api/InventoryController.java:37-41 — `POST /api/v1/inventory` returns 201 but does not set a `Location` header — Clients cannot discover the canonical URI of the created resource via standard REST semantics.

[HIGH] [API_CONTRACT] src/main/java/com/catalog/inventory/api/InventoryController.java:76-81 — `POST /api/v1/inventory/transfers` returns 200 OK and a success message `"Transfer completed"` — Transfer is implemented as a transactional operation, but the contract is not aligned to an async 202 workflow and may mislead clients and tests expecting `202 Accepted`.

[HIGH] [ARCHITECTURE] src/main/java/com/catalog/inventory/api/InventoryController.java:33-35 — Controller directly depends on `InventoryJournalRepository` (repository access from API layer) — Service-layer invariants can be bypassed and API behavior becomes harder to test/secure consistently.

[MEDIUM] [SECURITY] src/main/java/com/catalog/common/security/RateLimitingFilter.java:121-131 — Client identity trusts `X-Forwarded-For` and `X-Real-IP` unconditionally — Attackers can spoof these headers when not behind a trusted proxy to evade per-IP rate limits.

[MEDIUM] [SECURITY] src/main/resources/application.yml:65-69 — Default profile exposes `env` and `loggers` actuator endpoints and shows health details/components — If production is started without the `prod` profile, sensitive configuration/diagnostics can be exposed.

[MEDIUM] [RESILIENCE] src/main/resources/application.yml:70-76 — Custom `DEGRADED` status is mapped to HTTP 200 — A degraded dependency state is not surfaced as non-ready by HTTP status, which can keep traffic flowing during partial outages.

[MEDIUM] [RESILIENCE] src/main/resources/application.yml:83-87 — Readiness group includes `redis` and `catalog-storage` while `DEGRADED` maps to HTTP 200 — Instances may remain “ready” when Redis or storage is degraded/unavailable, despite correctness-impacting features (rate limiting/idempotency/storage verification).

[MEDIUM] [RESILIENCE] src/main/java/com/catalog/inventory/application/InventoryTransferService.java:37-75 — Uses `PESSIMISTIC_WRITE` locking without an explicit lock timeout hint — Under lock contention, requests can block for an unbounded time, causing thread starvation and cascading latency.

[MEDIUM] [PERF] src/main/java/com/catalog/product/infrastructure/ProductRepository.java:55-61 — Uses offset pagination via `Pageable` for product listing filters — Offset scans can become increasingly slow with large tables and are prone to inconsistent paging under concurrent writes.

[LOW] [SECURITY] src/main/resources/application-test.yml:21-27 — Test profile contains plaintext storage credentials (`access-key`, `secret-key`) — Even if non-production, committed credentials are frequently copied into real environments and can leak into logs/support artifacts.

>>> AUDIT COMPLETE. 18 findings: 1 CRITICAL 10 HIGH 6 MEDIUM 1 LOW
>>> Writing AUDIT_REPORT.md and starting Phase 3 immediately.

