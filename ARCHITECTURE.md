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
- **Order**: Customer order lifecycle.
- **Warehouse**: Physical storage location management.
- **Media**: Image storage and processing (S3/MinIO).

## Design Decisions
- **Denormalized Search**: PostgreSQL `tsvector` + trigrams for high-speed search without Elasticsearch.
- **Audit Immutability**: `inventory_journal` is append-only with DB-level `UPDATE` restrictions.
- **Resilience**: Resilience4j Circuit Breakers and Retries on all external I/O (S3, Redis).
- **Performance**: Java 21 Virtual Threads enabled for all async and I/O tasks.

## Known Limitations
- **Horizontal Scaling**: Redis is required for distributed rate limiting and idempotency.
- **Database Load**: Search projections are updated synchronously after transaction commit; extremely high mutation volume may delay search updates.
