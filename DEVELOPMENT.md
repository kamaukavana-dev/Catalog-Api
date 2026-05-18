# Development Guide

This guide provides instructions for setting up a local development environment and contributing to the Catalog API.

## 1. Prerequisites

- **Java 21**: Ensure you have a JDK 21 distribution installed.
- **Docker**: Docker is required to run the local development environment.
- **Maven**: The project uses Maven for dependency management and builds.

## 2. Local Environment Setup

The entire local development stack can be provisioned using Docker Compose.

1.  **Start the environment**:
    ```sh
    docker-compose up -d postgres redis jaeger prometheus grafana
    ```
    This command will start the following services:
    - `postgres`: The PostgreSQL database.
    - `redis`: The Redis cache.
    - `prometheus`: For collecting metrics.
    - `grafana`: For visualizing metrics.

2.  **Configure local environment**:
    - Copy `.env.example` to `.env` and set `DB_PASSWORD` plus any storage credentials.
    - Optional: configure API key guard with `CATALOG_REQUIRE_API_KEY` and `CATALOG_API_KEYS`.

3.  **Run the application**:
    ```sh
    mvn spring-boot:run
    ```

4.  **Accessing Services**:
    - **API**: `http://localhost:8080`
    - **PostgreSQL**: Port `5432`
    - **Grafana**: `http://localhost:3000`
    - **Prometheus**: `http://localhost:9090`

## 3. Building the Application

To build the project and run tests, use the following Maven command:

```sh
mvn clean install
```

This will compile the code, run all unit and integration tests, and package the application into a JAR file in the `target/` directory.

## 4. Code Generation

The project uses annotation processors to generate code at compile time.

- **Lombok**: Reduces boilerplate code (getters, setters, constructors).
- **MapStruct**: Generates mappers for converting between DTOs and domain entities.
- **QueryDSL**: Generates Q-types for type-safe queries.

If you are using an IDE like IntelliJ IDEA, ensure that annotation processing is enabled for the project to avoid compilation errors in the IDE.

## 5. Database Migrations

Database schema changes are managed by **Flyway**. To add a new migration, create a new SQL file in `src/main/resources/db/migration` following the Flyway naming convention: `V<VERSION>__<DESCRIPTION>.sql`.

Example: `V2__add_new_table.sql`

Flyway will automatically apply pending migrations on application startup.

## 6. Logging

For local development, logs are printed to the console in a human-readable format. SQL query logging is enabled via **P6Spy**, which can be configured in `spy.properties`.

In the `prod` profile, logging is switched to a structured JSON format for easier processing by log aggregation tools.
