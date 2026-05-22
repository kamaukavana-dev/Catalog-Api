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

