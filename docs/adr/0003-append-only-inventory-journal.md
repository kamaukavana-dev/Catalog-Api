# ADR-0003: Append-Only Inventory Journal
## Status: Accepted
## Context
Inventory tracking requires a perfect audit trail of all changes to prevent stock shrinkage and enable reconciliation. Standard CRUD on an inventory table is insufficient for auditing.

## Decision
We implement an append-only `inventory_journal` table. Every change to stock levels must create a journal entry. Database-level permissions are used to prevent UPDATES or DELETES on this table.

## Consequences
- Immutable audit trail of all stock movements.
- Reliable stock reconciliation.
- Slight increase in storage usage (negligible for the value provided).
