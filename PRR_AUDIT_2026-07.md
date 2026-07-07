# Production Readiness Review — catalog-api (2026-07-06)

> **Partly superseded (2026-07-07 docs audit).** Two figures below are now stale: the
> coverage/test numbers (this review: ~40 % instr, 109 tests) were re-measured at
> **74.1 % instr / 60.6 % branch, 238 tests** — see [`README.md`](README.md#testing). And
> finding **[M4]** (idempotency "not atomic, fails open") has since been **implemented** —
> `IdempotencyFilter` now uses an atomic `SET NX` claim with an explicit per-endpoint
> Redis-down policy. The remaining open findings (M3-residual, m6–m10, n11–n12) are still
> valid. Single source of truth for current status: [`DOCS_AUDIT_2026-07.md`](DOCS_AUDIT_2026-07.md).


Pre-launch PRR. Findings verified against source, not docs. Two of the four
"HIGH" items in the pre-existing `AUDIT_REPORT.md` were already fixed in code
(S3 circuit breaker present; virtual threads enabled) — they are stale and are
**not** re-reported here. The most severe issue in the codebase was one that
report missed entirely: the build was red and the entire integration suite was
non-functional.

Branch with fixes: `prr-remediation` (4 commits). Nothing pushed.

---

## 1. Findings by severity and domain

| Severity | Count | Fixed this pass | Open |
|----------|-------|-----------------|------|
| BLOCKER  | 1     | 1               | 0    |
| MAJOR    | 3     | 2               | 1    |
| MINOR    | 6     | 1               | 5    |
| NIT      | 2     | 0               | 2    |

| Domain (panelist)            | Findings |
|------------------------------|----------|
| Correctness & Data Integrity | B1, M4                 |
| Security                     | M2, m7, m8             |
| Scalability & Performance    | M3 (shared), m10, n12  |
| Reliability & Operability    | B1 (shared), M3, m9, n11 |
| API & Domain Design          | m5, m6                 |

---

## 2. The findings

### BLOCKER

**[B1] `StartupValidationConfig` crashes the ApplicationContext under Testcontainers → build red, 0/25 integration tests run. — FIXED**
`common/config/StartupValidationConfig.java:37` injected `@Value("${spring.datasource.url:}")`.
The `:` default guards only the outer `spring.datasource.url` reference. In
`application.yml` that property's literal value is `${DB_URL}` (no default), so
Spring then resolves the nested `DB_URL` and throws
`PlaceholderResolutionException: Could not resolve placeholder 'DB_URL'`.
Under Testcontainers the datasource is supplied by a `JdbcConnectionDetails`
bean (`@ServiceConnection`), so `spring.datasource.url` is never materialized
and `DB_URL` is unset. Bean construction failed → context refresh failed →
every `@SpringBootTest` errored with "ApplicationContext failure threshold
exceeded". `mvn verify` exited 1.
*Repro:* `mvn test-compile failsafe:integration-test` on the original tree → 25 errors, BUILD FAILURE.
*Why BLOCKER not MAJOR:* the concurrency/inventory correctness net (optimistic
locking, oversell prevention, transfer deadlock ordering) was entirely
unverified in CI. Any regression in the hottest code shipped undetected.
*Fix:* validate the injected `DataSource` (probe `getConnection().isValid(2)`)
instead of dereferencing the property. Works in every environment and is
strictly stronger — catches wrong host/port/db/credentials at boot, which is
the class's stated purpose. Proof: 25 ITs go ERROR → PASS.

### MAJOR

**[M2] Rate-limit identity trusts attacker-controlled `X-Forwarded-For`. — FIXED**
`common/security/RateLimitingFilter.java:extractClientIp` took
`forwarded.split(",")[0]` — the leftmost XFF entry, which any client can set.
Rotating the header mints a fresh token bucket per request, bypassing the
primary DoS control on write and bulk-import endpoints. `X-Real-IP` had the
same flaw.
*Repro:* two requests with `X-Forwarded-For: 9.9.9.9` then `7.7.7.7` produced
two different rate-limit keys.
*Why MAJOR not BLOCKER:* defeats a protection, not a direct breach/data-loss.
*Fix:* new `catalog.security.trusted-proxy-count` (default 1); client IP is
taken that many hops from the right of XFF. Regression test asserts a rotated
leftmost entry still maps to one key.

**[M3] S3 client has no timeouts; image processing holds a DB transaction across S3 I/O → Hikari pool exhaustion. — PARTIALLY FIXED (timeouts landed; tx coupling open)**
`media/config/StorageConfig.java:s3Client()` built a bare `UrlConnectionHttpClient`
with no connect/socket timeout and no `apiCallTimeout`. The resilience4j
`timelimiter` for `object-storage` is declared but never applied (no
`@TimeLimiter`; it only wraps `Future` returns). A hung S3 call therefore never
returns, never counts as a failure, and never trips the circuit breaker.
`media/product/application/ImageProcessingService.java:30` runs those calls
inside `@Transactional`, pinning a Hikari connection (pool size 10) for the
whole call. ~10 concurrent uploads during storage slowness starve the pool and
503 all DB traffic.
*Why MAJOR:* realistic outage, but needs storage degradation to trigger.
*Fix (landed):* connect/socket/api-call timeouts (5s/10s/15s, configurable),
so a hung call fails in seconds and the breaker can observe it. Regression test
uses a local accept-but-never-respond server.
*Still open:* `processImage` should not hold a DB transaction across network +
`ImageIO` decode. Restructure to do S3/CPU work outside the tx and persist
status in a short tx. Left for a focused change — behavioural, deserves its own
review.

**[M4] `IdempotencyFilter` is not atomic and fails open. — OPEN**
`common/security/IdempotencyFilter.java:75-99` does read-then-execute-then-write
with no in-progress reservation. Two concurrent retries of the same key both
miss the cache and both execute the mutation. `getCachedResponse` also returns
`null` on any Redis error (`:139-142`), silently disabling idempotency when
Redis is down. Reservations are saved by the DB unique index
`uq_ir_active_inventory_reference`, but any resource without a uniqueness guard
(and everything when Redis is down) can be duplicated by a double-POST.
*Why MAJOR not BLOCKER:* the worst money-path (reservations) is DB-guarded, so
this is duplicate side effects, not guaranteed double-charge.
*Fix (proposed):* `SET key <in-progress> NX PX <window>` to claim the key
before executing; on a losing `NX`, return 409/425 or poll for the stored
result. Decide the Redis-down policy explicitly (fail-closed for unsafe
methods).

### MINOR

- **[m5] Bulk import rejected its own documented `RECONCILIATION` token. — FIXED.** `BulkInventoryProcessor.java` switched on `"RECONCILE"` while the error message advertised `"RECONCILIATION"`. Now accepts both; message corrected to `RECEIVE, RECONCILE`.
- **[m6] Order subsystem is dead code. — OPEN.** `order/application/OrderService.java` has zero callers; no `OrderController`/`OrderModule`. Tables `orders`/`order_line_items` (V12) are never written by any API path. Either wire it or delete it and the migration weight; shipping unreachable write models invites drift.
- **[m7] Actuator over-exposure outside prod. — OPEN.** Base `application.yml` exposes `env,loggers` with `health.show-details: always`; only `application-prod.yml` narrows to `health,info,prometheus`. Actuator is unauthenticated (ApiKeyAuthFilter guards only `/api/`). Any non-prod profile reachable on a network leaks env (incl. DB/redis creds) via `/actuator/env`. Gate exposure on profile the other way (deny by default).
- **[m8] API-key auth exempts all reads. — OPEN (confirm intent).** `ApiKeyAuthFilter.java:58` skips GET/HEAD/OPTIONS even when `require-api-key=true`, so every read endpoint is unauthenticated. Fine only if a gateway enforces read auth; if this is the only control, catalog data is world-readable.
- **[m9] Transient storage outage marks images permanently FAILED. — OPEN.** `ImageProcessingService` catches `StorageUnavailableException` (circuit open) and calls `image.markFailed()` with no retry path; a 60s storage blip permanently fails uploads.
- **[m10] `ReservationCleanupJob` has no distributed lock. — OPEN (low).** `@Scheduled` runs on every instance. Correctness is preserved by `findActiveByIdWithLock` + `isExpired()` recheck, so this is wasted work, not double-release. A `ShedLock` would remove the contention.

### NIT

- **[n11] `GlobalExceptionHandler.activeProfile` is injected but never read** — the "show details off-prod" behaviour was never implemented (the generic handler always hides details, which is safe, but the field is dead).
- **[n12] Cache eviction is fire-and-forget `@Async` `AFTER_COMMIT`** — a dropped eviction (executor saturation / crash between commit and async run) leaves a stale entry until TTL. Correct pattern, bounded blast radius; noted for awareness.

---

## 3. Verified-good (so the reader can trust the scope)

- Inventory domain invariants are sound: `reserve`/`transferOut` gate on
  available qty; `completeSale` checks both counts; optimistic `@Version` +
  `@Retryable` on every mutation; DB `CHECK(reserved_quantity <= quantity)`.
- Warehouse transfer acquires pessimistic locks in UUID order — deadlock-safe.
- Reservation idempotency is enforced by a partial unique index, not just app code.
- All repository queries are parameterized JPQL — no injection surface found.
- Datasource genuinely fails fast (no `DB_URL` default; Hikari `initializationFailTimeout`).
- CORS disables credentials when origin is `*`; security headers (CSP `none`, gated HSTS) are correct.
- Migrations are clean and ordered; V15 is a documented no-op. Dependencies are current (Spring Boot 3.4.5); no ancient/CVE-heavy libs seen.

---

## 4. What was fixed vs. what remains

**Fixed & proven (branch `prr-remediation`):** B1, M2, M3-timeouts, m5 — each a
commit with a regression test that fails before and passes after.

**Open, with honest reason:**
- **M4 (idempotency atomicity)** — behavioural change to a request-path filter;
  needs a deliberate Redis-down policy decision. Higher risk than a one-session drop-in.
- **M3-tx-coupling** — restructuring `processImage`'s transaction boundary is a
  design change deserving its own review.
- **m6/m7/m8** — require product decisions (delete vs. wire orders; actuator
  exposure policy; whether reads are authenticated) rather than a mechanical fix.
- **m9/m10/n11/n12** — real but low urgency; batched for a follow-up.

---

## 5. The three riskiest things still true (next 90 days, 3am framing)

1. **Idempotency doesn't hold under concurrency and vanishes when Redis blips (M4).**
   The common retry pattern — client fires a second POST because the first
   *looked* hung — runs the mutation twice for any resource lacking a DB unique
   constraint. This is the highest-probability "why is there two of everything"
   page. Reservations are safe; product/warehouse/brand creation and anything
   added next are not.

2. **Image processing still couples a DB connection to storage latency (M3 residual).**
   Timeouts cap a hang at ~15s instead of forever, but a burst of uploads during
   an S3/MinIO slowdown can still park up to 10 connections for up to 15s each
   and starve unrelated traffic into 503s. The timeout shrank the blast radius;
   it didn't remove the coupling.

3. **The safety net was resurrected today, and it's thin where it isn't inventory.**
   40.2% instruction / 36.7% branch coverage, concentrated in inventory.
   Controllers, media, search, and bulk-product paths are lightly or mock-only
   tested. Green means "the tested paths pass," and until this fix those tests
   didn't run at all. First real traffic will exercise the untested paths first.

---

## 6. Coverage — actual, post-fix

Merged unit + integration (`target/site/jacoco-merged`): **40.2% instructions
(7,923 / 19,696), 36.7% branches (375 / 1,023).** Tests: **84 unit + 25
integration, all passing; `mvn verify` (OWASP step aside) BUILD SUCCESS.**
Before this pass the 25 integration tests contributed ~nothing (all errored),
so this is the first honest merged number the project has had.
