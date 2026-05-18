# Catalog API

A high-performance, production-grade ecommerce catalog and inventory management system built with Spring Boot 3.4 and Java 21.

## 🚀 Key Features

- **Multi-Warehouse Inventory Management**: Append-only journal, reservations with expiry, and pessimistic-locked transfers.
- **Advanced Product Search**: Denormalized read model using PostgreSQL GIN indexes and trigram search.
- **Production-Grade Resilience**: Distributed rate limiting, idempotency keys, and circuit breakers for external storage.
- **Observability-First**: Full OpenTelemetry integration, structured JSON logging, and custom Micrometer metrics.
- **Scalable Bulk Operations**: Chunked, idempotent async processing for inventory and product updates.

## 🛠 Tech Stack

- **Backend**: Java 21, Spring Boot 3.4
- **Database**: PostgreSQL 16 (with `pg_stat_statements` and GIN/Trigram)
- **Cache**: Redis 7
- **Search**: PostgreSQL Native Full-Text Search
- **Storage**: AWS S3 / MinIO
- **Observability**: Prometheus, Grafana, Jaeger (OTEL)
- **DevOps**: Docker, Flyway

## 🏗 Architecture

The project follows a **Modular Monolith** approach with a focus on Clean Architecture principles:

- **api**: Thin controllers handling HTTP request/response.
- **application**: Orchestration, transaction boundaries, and use cases.
- **domain**: Pure business logic and entity-level rules.
- **infrastructure**: Adapters for DB, Redis, and external APIs.

For detailed architecture diagrams and design decisions, see [ARCHITECTURE.md](./ARCHITECTURE.md).

## 🚦 Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 21 (for local development)

### Local Development Setup
1. Clone the repository.
2. Create your local environment file: `cp .env.example .env`.
3. Start infrastructure services:
   ```bash
   docker-compose up -d postgres redis jaeger prometheus grafana
   ```
4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

Database credentials are configured via `.env` (see `.env.example`).
Datasource env vars used by the app:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## 📈 Monitoring & Observability

- **Grafana**: [http://localhost:3000](http://localhost:3000) (Admin/admin)
- **Prometheus**: [http://localhost:9090](http://localhost:9090)
- **Jaeger UI**: [http://localhost:16686](http://localhost:16686)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

## 🔐 Security & Hardening

- **API Key Guard**: Optional `X-Api-Key` enforcement for mutation endpoints (see `CATALOG_REQUIRE_API_KEY`).
- **Rate Limiting**: Per-IP token bucket (Redis-backed with Caffeine fallback).
- **Idempotency**: `X-Idempotency-Key` support for POST mutation endpoints.
- **Graceful Shutdown**: 30-second timeout for in-flight requests.
- **Non-Root Docker**: Application runs as a restricted user in production.

## 📄 License
MIT
