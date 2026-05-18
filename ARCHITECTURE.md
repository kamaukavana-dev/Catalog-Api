# Architecture Documentation

## Core Design Philosophy

The Catalog API is designed for **high consistency**, **high availability**, and **observability**. It prioritizes data integrity for inventory while ensuring sub-100ms search latency through denormalization.

## 1. Inventory Consistency Model

We use a hybrid locking strategy to balance throughput and correctness:

- **Checkouts (Reservations)**: Use **Optimistic Locking** (`@Version`) with **Spring Retry**. This handles high concurrency on SKU-level stock without blocking other SKU operations.
- **Transfers (Warehouse-to-Warehouse)**: Use **Pessimistic Locking** (`SELECT FOR UPDATE`) with **deterministic lock ordering** (by UUID) to prevent deadlocks.
- **Auditability**: Every single mutation (Receive, Sale, Reserve, Reconcile) is recorded in an **append-only `inventory_journal`** table. The application database user has `UPDATE` and `DELETE` privileges revoked on this table.

## 2. Search Projection Strategy

To avoid expensive JOINs and real-time aggregations (N+1 problems) during product browsing, we maintain a `product_search_projection` table.

- **Write path**: When a Product, Variant, or Inventory record is mutated, a `TransactionalEventListener` fires (AFTER_COMMIT). An `@Async` worker recalculates the projection for that product.
- **Read path**: Storefront search queries only the projection table using GIN indexes for full-text search.
- **Catch-up**: A scheduled job runs every 10 minutes to sync any missed events.

## 3. Bulk Job Infrastructure

Bulk updates (CSV imports) are processed using a **Job-based Batch Pattern**:

- **Idempotency**: Each job has a `session_id`. Retrying the same session returns the existing job status.
- **Transaction Isolation**: Jobs are partitioned into batches (e.g., 100 rows). Each batch runs in `Propagation.REQUIRES_NEW`. If one row in a batch fails, the whole batch rolls back, but other batches remain committed.
- **Progress Tracking**: Real-time updates on `processed_rows` and `failed_rows`.

## 4. Resilience Patterns

- **Circuit Breakers (Resilience4j)**: Applied to S3/Object Storage. If MinIO or S3 is down, the system fails fast rather than timing out threads.
- **Rate Limiting**: Implemented via `Bucket4j` in a Servlet Filter.
- **Caching**: Multi-level caching. Redis for shared state (Categories, Brands, Search results) and Caffeine for local transient state (Rate limit buckets).

## 5. Deployment Strategy

- **Multi-stage Dockerfile**: Separates build and runtime environments.
- **Profile-based Config**: `application-prod.yml` enforces strict security defaults (e.g., no exposed `env` actuator).
- **Graceful Shutdown**: Tomcat waits for active requests to finish before stopping.
