# ADR-0003: Append-Only Inventory Journal
## Status: Accepted
## Context
Inventory tracking requires a perfect audit trail of all changes to prevent stock shrinkage and enable reconciliation. Standard CRUD on an inventory table is insufficient for auditing.

## Decision
We implement an append-only `inventory_journal` table. Every change to stock levels must create a journal entry. Database-level permissions are *intended* to prevent UPDATES or DELETES on this table.

> **Implementation status (2026-07):** The DB-level `REVOKE UPDATE/DELETE` is **not yet implemented** — no `REVOKE`, trigger, or rule exists in any migration; `V8` only carries a comment asserting it. Today the append-only guarantee is enforced by application convention (the entity has no update path; the service only inserts). Adding the actual `REVOKE`/trigger is tracked in the README Roadmap.

## Consequences
- Immutable audit trail of all stock movements.
- Reliable stock reconciliation.
- Slight increase in storage usage (negligible for the value provided).
