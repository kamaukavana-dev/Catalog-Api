# Performance and Scalability

This document describes the features and strategies implemented in the Catalog API to ensure high performance and scalability.

## 1. Caching

The application employs a multi-level caching strategy to reduce database load and improve response times.

- **Distributed Cache (Redis)**: Redis is the primary distributed cache, used for caching data that needs to be shared across multiple instances of the application. This is suitable for caching entities, query results, and other frequently accessed data.
  - **Evidence**: `spring-boot-starter-data-redis` dependency, `RedisConfig`.

- **In-Memory Cache (Caffeine)**: Caffeine is used for high-performance, in-memory caching within a single application instance. This is ideal for very frequently accessed, small-footprint data where the overhead of network latency to Redis is undesirable. A primary use case is the storage of rate-limiting buckets.
  - **Evidence**: `com.github.ben-manes.caffeine:caffeine` dependency.

## 2. Asynchronous Processing

For long-running tasks, the application uses Spring's asynchronous method execution (`@Async`). This prevents blocking the main request thread and ensures the API remains responsive. A prime example is the bulk product update feature, which processes CSV files in the background.

**Evidence**:
- `@EnableAsync` on `CatalogApplication`.
- `@Async` on `ProductService#bulkUpdateFromCsv`.

## 3. Database Performance

- **Query Optimization**: The application uses **QueryDSL** for building type-safe dynamic queries. This allows for more complex and potentially more efficient queries than what can be achieved with standard JPA repository methods alone, especially for filtering and searching.
  - **Evidence**: `com.querydsl:querydsl-jpa` dependency.

- **Connection Pooling**: By default, Spring Boot with JPA uses **HikariCP** for high-performance database connection pooling. This is configured via `application.yml`.

- **Indexing**: While specific indexing strategies are defined in the database schema (via Flyway migrations), the use of unique constraints on business keys (e.g., `slug`) also creates indexes, improving lookup performance for these columns.
  - **Evidence**: `@UniqueConstraint` annotations on domain entities.

## 4. Application Resilience

Resilience patterns are implemented to ensure the application remains stable under load and during partial failures.

- **Circuit Breakers (Resilience4j)**: Protects the application from cascading failures by isolating failing external dependencies.
  - **Evidence**: `resilience4j-spring-boot3` dependency.

- **Retries (Spring Retry)**: Automatically retries failed operations, which is particularly useful for transient errors or optimistic locking conflicts.
  - **Evidence**: `spring-retry` dependency.

## 5. Scalability Model

The application is designed to be **horizontally scalable**. As a stateless service (with state offloaded to PostgreSQL and Redis), multiple instances of the Catalog API can be run behind a load balancer to handle increased traffic. The container-based deployment model (`Dockerfile`) supports this architecture.

**Limitations**:
- The ultimate scalability will depend on the performance of the backing services (PostgreSQL and Redis).
- The effectiveness of the caching strategy is crucial for reducing database bottlenecks as load increases.

