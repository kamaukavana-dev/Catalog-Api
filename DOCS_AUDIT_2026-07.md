# Documentation Accuracy Audit — 2026-07-07

Documentation-only pass. **No source or behaviour was changed.** Every claim carried
into the rebuilt [`README.md`](README.md) was re-verified against the code on this branch
(`prr-remediation`) and against a fresh `mvn clean verify`.

- Verdicts: **TRUE** (matches code) · **STALE** (was true, now out of date) ·
  **FALSE** (contradicts code) · **UNVERIFIABLE** (no evidence in code).

---

## 1. Findings table

### Numbers (re-derived from `mvn clean verify` on HEAD, 2026-07-07)

| Claim | Where claimed | Verdict | Evidence / correction |
|---|---|---|---|
| Coverage ≈ 40.2 % instructions / 36.7 % branches | `PRR_AUDIT_2026-07.md:177` | **STALE** | Fresh merged JaCoCo: **74.1 % instr / 60.6 % branch / 74.3 % line / 58.3 % method**. The old figure predates the `test(bulk|search|variant)` commits on this branch **and** predates the JaCoCo generated-source exclusions. |
| Coverage 39.9 % instr / 35.9 % branch / 44.7 % line; 103 tests | `AUDIT_REPORT.md:292-294,288` | **STALE** | Superseded; see above. Current tests: **238** (109 unit + 129 IT). |
| Test count 84 unit + 25 IT = 109 | `PRR_AUDIT_2026-07.md:178` | **STALE** | Now **109 unit + 129 IT = 238**, all passing. |
| "Full test suite: 53 tests … 10 skipped" | `CHANGE_SUMMARY.md:16` | **STALE** | Intermediate pass from May; superseded by the 238-test run. |
| ADR search target "up to 100k products" | `docs/adr/0002:12` | **UNVERIFIABLE** | Design target, no load test in repo. Kept in README as an explicit *target*, not a measured figure. |

### Tech stack (vs `pom.xml`)

| Claim | Where | Verdict | Evidence |
|---|---|---|---|
| Spring Boot 3.4.5, Java 21 | `README.md:69-70` (old) | **TRUE** | parent POM `3.4.5`, `<java.version>21` |
| Flyway 10.22.0, QueryDSL 5.1.0, MapStruct 1.6.3, Lombok 1.18.36, Bucket4j 8.10.1, Resilience4j 2.2.0, commons-csv 1.10.0, logstash-logback 7.4, Testcontainers 1.21.4, WireMock 3.0.4, JaCoCo 0.8.15, OWASP 9.0.9, AWS SDK BOM 2.25.11 | `README.md` (old) | **TRUE** | all present in `pom.xml` |
| P6Spy 1.9.1 | `DEVELOPMENT.md:69` | **TRUE** | `pom.xml` `local` profile only |
| Testcontainers 1.20.4 | `PHASE0_OUTPUT.md:35` | **STALE** | `pom.xml` now `1.21.4` |

### Features / capabilities (vs source)

| Claim | Where | Verdict | Evidence |
|---|---|---|---|
| API-key auth on writes, `X-Api-Key`, constant-time compare | `SECURITY.md:44-50` | **TRUE** | `common/security/ApiKeyAuthFilter.java` (`MessageDigest.isEqual`) |
| Rate limiting: reads 100/min, writes 20/min, bulk 5/min, per IP, Redis + Caffeine fallback | `README.md:327`, `SECURITY.md:15-23` | **TRUE** | `RateLimitingFilter.java:32-34,133-138`, `RedisTokenBucketRateLimiter` |
| Idempotency: `X-Idempotency-Key`, cached 24h, replay header | `README.md:335` | **TRUE** | `IdempotencyFilter.java` |
| Idempotency **atomic claim / M4** ("not atomic, fails open") | `PRR_AUDIT_2026-07.md:91-104` | **STALE (now fixed)** | Code now uses `setIfAbsent` (SET NX PX) + explicit per-endpoint fail-closed/open policy — `IdempotencyFilter.java:93-102,186-215`. The PRR "proposed fix" is implemented on this branch. |
| Circuit breakers + retry on S3 & Redis | `ARCHITECTURE.md:24`, `PERFORMANCE.md:37` | **TRUE** | `resilience4j.yml` instances `object-storage`, `redis`; `S3StorageService`, `CacheEvictionService` |
| Caching: Redis + Caffeine, per-cache TTLs | `PERFORMANCE.md:7-12` | **TRUE** | `common/config/RedisConfig.java`, `ProductSearchCacheService` |
| Security headers (nosniff, DENY, CSP, HSTS in prod) | — | **TRUE** | `SecurityHeadersFilter.java` |
| Full-text search: GIN `tsvector` + `pg_trgm`, denormalized projection | `README.md:325`, `ADR-0002` | **TRUE** | `db/migration/V9__*.sql` (`idx_psp_searchable_text` GIN), `product/application/search/*` |
| Async bulk import (CSV, job status polling, idempotent by session) | `README.md:3`, `DECISIONS.md` | **TRUE** | `BulkImportController`, `BulkInventoryService`, `ProductBulkUpdateService` |
| S3 via AWS SDK v2, presigned PUT URLs, bounded timeouts | — | **TRUE** | `media/config/StorageConfig.java`, `S3StorageService` |
| Virtual threads enabled | `README.md:329`, `ARCHITECTURE.md:25` | **TRUE** | `application.yml:1-4` `spring.threads.virtual.enabled: true` |
| Optimistic locking + retry + append-only journal | `README.md:333` | **TRUE** | `BaseEntity` `@Version`, `InventoryService` `@Retryable`, `InventoryJournal` |
| DB extensions `pgcrypto`, `pg_trgm`, `unaccent`, **`pg_stat_statements`** | `README.md:279` (old) | **FALSE** | `V1__init_extensions.sql` creates only `pgcrypto`, `pg_trgm`, `unaccent`. **No `pg_stat_statements`.** |
| Inventory journal has **"DB-level UPDATE restrictions"** / "DB permissions prevent UPDATE/DELETE" | `ARCHITECTURE.md:23`, `ADR-0003:7`, `InventoryJournal.java:18` | **FALSE** | No `REVOKE`, trigger, or rule in any migration. `V8` only has a **comment** asserting it. Immutability is application convention, not DB-enforced. |
| `order` module is part of the domain | `ARCHITECTURE.md:17`, `ADR-0001:4` | **STALE / dead** | `order/application/OrderService.java` has no controller and no callers; `orders`/`order_line_items` (V12) are never written. |
| Actuator narrowed under prod | `README.md:275` | **TRUE but** | prod narrows to `health,info,prometheus`; **base `application.yml` exposes `env,loggers` + `show-details: always`** (over-exposed off-prod). |
| "No blockers" | `BLOCKERS.md:1` | **STALE / contradictory** | Contradicts `PRR_AUDIT_2026-07.md` open findings (M3-residual, m6–m10). Reconciled — see §3. |
| Endpoints list (~60 routes, all contexts) | `README.md` / `API.md` | **TRUE** | Cross-checked against all 8 controllers; matches. `API.md:22` lists `POST /api/v1/inventory/adjust` — actual route is `PATCH /api/v1/inventory/{id}/stock`. |

### Marketing / aspirational language (banned unless backed by evidence)

| Phrase | Where | Action |
|---|---|---|
| "production-grade" | `README.md:3` (old), `pom.xml:20` `<description>` | Removed from README (kept the POM description untouched — code, not docs). |
| "High-performance search" | `API.md:26` | Dropped in README; stated as GIN/tsvector fact instead. |
| "horizontally scalable" | `PERFORMANCE.md:45` | Not carried into README (no load-test evidence). |

---

## 2. Diagrams generated (Phase 3)

All three are committed SVGs under `docs/images/`, produced by the committed, re-runnable
[`docs/images/generate_diagrams.py`](docs/images/generate_diagrams.py) (no external tool
needed). No stock art or hand-drawing.

| File | What it is | Derived from |
|---|---|---|
| `module-dependencies.svg` | Inter-context coupling graph | **Parsed live** from `import com.catalog.*` statements across `src/main/java`. Edge labels = import counts. Surfaces the real `common`→domain back-coupling and the unreachable `order` node. |
| `reservation-write-path.svg` | Idempotent reservation write path | The real filter `@Order` values, `IdempotencyFilter` (atomic SET-NX claim shown as its own stage — the M4 point), and `InventoryService.reserveStock` (`@Transactional`/`@Retryable`, journal append) with the commit boundary drawn explicitly. |
| `erd-core.svg` | Core catalog ER model | The `@Entity` classes + Flyway `V1..V17` (FKs, unique constraints, join tables, the append-only journal). |

The structure diagrammed cleanly; no diagram had to be faked for coupling reasons. The one
honest wrinkle worth stating is the `common`→`product/inventory/media` back-edge — shown,
not hidden.

---

## 3. Changelog — what was corrected/removed and why

**README.md (full rebuild):**
- Removed "production-grade" and other adjectives with no backing evidence.
- Replaced the hand-authored Mermaid architecture block with three **code-generated** SVGs.
- **Corrected coverage/test numbers** to the fresh build: 74.1 % instr / 238 tests
  (was ~40 % / ~109 — stale).
- **Deleted the `pg_stat_statements` extension claim** (not created by `V1`).
- **Reworded the journal claim** from "DB-level immutability" to "append-only by
  application convention" and moved the unenforced-`REVOKE` gap into Known limitations.
- Added a required **Known limitations** section (journal REVOKE gap, dead `order` module,
  actuator off-prod exposure, read endpoints unauthenticated, image-processing tx coupling,
  search-cache deserialization behaviour, cleanup-job no lock, rate-limit fail-open).
- Added a **Roadmap** section separating planned items from built ones.
- Recorded the **M4 atomic idempotency claim as implemented** (the PRR listed it as a
  proposed fix; the code on this branch has it).

**Ancillary docs reconciled (to remove doc-vs-doc contradictions):**
- `BLOCKERS.md` — updated so "No blockers" no longer contradicts the open PRR findings.
- `AUDIT_REPORT.md`, `PRR_AUDIT_2026-07.md` — added a short banner marking their coverage/
  test numbers and M4 status as superseded by this pass, pointing to the README as the
  single source of truth (historical findings left intact).
- `docs/adr/0003-append-only-inventory-journal.md` — annotated that the DB-level
  `REVOKE` is not yet implemented (the ADR remains "Accepted" as intent).

---

## 4. Still not documentable as a strength (belongs in Known limitations, not spin)

1. **Journal immutability is not actually DB-enforced** — the strongest correction of this
   pass. Comments claim a `REVOKE` that does not exist.
2. **`order` module is unreachable dead code** carrying a live migration (`V12`).
3. **All read endpoints are unauthenticated** even with `require-api-key=true`; safe only
   behind a gateway that enforces read auth.
4. **Actuator leaks `env`/`loggers` off-prod** (unauthenticated).
5. **Image processing pins a DB connection across S3 + `ImageIO` decode** — pool-starvation
   risk under storage slowness (timeouts bound the call; the tx coupling does not).
6. **Coverage is uneven** — the media/S3 pipeline is close to untested (~0–5 %), and method
   coverage overall is 58 %.
7. **Rate limiting degrades to per-node** when Redis is down (fail-open by design).
8. **Scheduled cleanup has no distributed lock** — duplicated work across instances.
