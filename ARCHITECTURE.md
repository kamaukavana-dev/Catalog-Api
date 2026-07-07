# Architecture Overview

## Layer Dependency Rules
| Layer | May Depend On |
| --- | --- |
| API | Application, Domain, Common |
| Application | Domain, Infrastructure, Common |
| Domain | Common (minimal) |
| Infrastructure | Domain, Common |

## Module Inventory
- **Category**: Hierarchical management of product categories.
- **Brand**: Manufacturer and brand management.
- **Product**: Core catalog management (DRAFT/ACTIVE/ARCHIVED).
- **Variant**: Sellable units with attributes and pricing.
- **Inventory**: Real-time stock tracking with append-only auditing.
- **Order**: Domain model only — `orders`/`order_line_items` tables exist (V12) but there is no `OrderController` and no caller of `OrderService`; not reachable via the API. See Known Limitations.
- **Warehouse**: Physical storage location management.
- **Media**: Image storage and processing (S3/MinIO).

## Design Decisions
- **Denormalized Search**: PostgreSQL `tsvector` + trigrams for high-speed search without Elasticsearch.
- **Audit Immutability**: `inventory_journal` is append-only. Note: immutability is enforced by application convention (the entity has no update path and the service only inserts). The DB-level `REVOKE UPDATE/DELETE` described in `V8`/`InventoryJournal.java` comments is **not yet implemented** in any migration — see Known Limitations.
- **Resilience**: Resilience4j Circuit Breakers and Retries on all external I/O (S3, Redis).
- **Performance**: Java 21 Virtual Threads enabled for all async and I/O tasks.

## Known Limitations
- **Horizontal Scaling**: Redis is required for distributed rate limiting and idempotency.
- **Database Load**: Search projections are updated after transaction commit (`AFTER_COMMIT`); search is eventually consistent and high mutation volume may delay search updates.
- **Journal immutability not DB-enforced**: no `REVOKE`/trigger exists; append-only rests on application convention.
- **`order` module is unreachable dead code** (no controller/callers).
- **Actuator over-exposed off-`prod`** (`env,loggers`, unauthenticated).

For the full, code-verified limitation list and current test/coverage numbers, see [`README.md`](README.md) and [`DOCS_AUDIT_2026-07.md`](DOCS_AUDIT_2026-07.md).
