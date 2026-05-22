# Decisions Log

This file records defensive, production-safe decisions made autonomously during this remediation session (2026-05-22).

## 2026-05-22 — Tooling: Maven/Docker Execution Context

* Decision: Use a repo-local Maven distribution at `.tools/apache-maven-3.9.9/bin/mvn` for all builds/tests going forward.
* Why: System `mvn` is 3.8.7 (below the required 3.9+). A Maven Wrapper (`./mvnw`) is not present, so the safest self-contained path is a local Maven install under the project root.
* Alternatives considered:
  * Install/upgrade system Maven: rejected (would be global/system state and not reliably available in this environment).
  * Add Maven Wrapper: rejected for now (requires downloading wrapper artifacts; local Maven achieves the same goal with less repo churn).

* Decision: Run Docker/Git operations with elevated permissions when needed.
* Why: Docker socket (`/var/run/docker.sock`) and `.git/` write operations are denied in the sandbox context; elevated execution is required for Testcontainers and committing incremental changes.

## 2026-05-22 — Tests: InventoryService “Adjust DOWN” Interpretation

* Decision: Interpret “adjust stock DOWN” test requirements as using `AdjustStockRequest.AdjustmentType.RECONCILE` to a lower absolute quantity (including zero).
* Why: `InventoryService.adjustStock()` supports only `RECEIVE` and `RECONCILE`; there is no decrement operation type. Reconcile is the safest supported way to reduce quantity.
* Alternative considered: Drive “DOWN” via reservations/sales or transfers; rejected for this class because the requirements explicitly reference `adjustStock(...)`.

## 2026-05-22 — Bulk Import Semantics

* Decision: Make bulk inventory imports fail-fast after the first row error, marking all remaining rows as failed without applying further inventory/journal changes.
* Why: For ERP-driven bulk adjustments, partial application after a detected data error increases reconciliation risk. Fail-fast keeps the import outcome predictable and easier to remediate.
* Alternatives considered:
  * Continue processing independent rows after an error: rejected for this codebase because the requested acceptance criteria expects rows 76-150 to fail after row 76 is invalid.

* Decision: Use `IN_PROGRESS` as the in-flight bulk import job status (while remaining backward compatible with legacy `PROCESSING` in DB constraints).
* Why: Required by the test/contract expectations and clearer than `PROCESSING` for external clients.

* Decision: Dispatch bulk import work via an AFTER_COMMIT application event listener.
* Why: Publishing async work from inside the submission transaction can run before the job row is committed, causing status updates to be dropped (job appears to jump from PENDING directly to COMPLETED).
