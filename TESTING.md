# Testing Strategy

This document outlines the testing strategy for the Catalog API, which is designed to ensure code quality, correctness, and reliability.

## 1. Overview

The project employs a multi-layered testing approach, including unit tests, integration tests, and repository tests. The goal is to have fast, isolated unit tests for business logic and high-fidelity integration tests that validate the application's behavior with real infrastructure.

## 2. Unit Tests

- **Purpose**: To test individual components (e.g., services, domain objects) in isolation.
- **Location**: `src/test/java` in the same package as the class under test, with a `Test` suffix.
- **Mocks**: Dependencies are mocked using **Mockito**. This ensures that unit tests are fast and not dependent on external systems.
- **Example**: `ProductServiceTest.java` tests the `ProductService` class, mocking the `ProductRepository` and other dependencies.

## 3. Integration Tests

- **Purpose**: To test the application's components together, including integration with external infrastructure like the database and cache.
- **Framework**: **Testcontainers** is used to spin up real Docker containers for backing services (PostgreSQL, Redis) during the test execution. This provides a high degree of confidence that the application will behave correctly in a production-like environment.
- **Annotations**: Integration tests are typically annotated with `@SpringBootTest` to load the full application context.
- **Configuration**: A specific `application-test.yml` profile is used for tests, which is configured to work with the services managed by Testcontainers.
- **Example**: `BrandRepositoryTest.java` uses a `@Testcontainers` PostgreSQL container to test the `BrandRepository` against a real database.

## 4. Repository Tests

- **Purpose**: To specifically test the correctness of Spring Data JPA repositories, including custom queries.
- **Annotation**: These tests are annotated with `@DataJpaTest`, which is a specialized test slice that focuses only on JPA components. It uses an in-memory database by default, but is configured in this project to use Testcontainers for more realistic testing.
- **Example**: `CategoryRepositoryTest.java`.

## 5. Running Tests

All tests are run as part of the standard Maven build cycle.

```sh
mvn clean install
```

This command executes all tests in the project.

## 6. Testing Philosophy

- **Write tests for all new features**: All new code should be accompanied by meaningful tests.
- **Focus on business logic in unit tests**: Unit tests should validate the correctness of business rules and algorithms.
- **Use integration tests for interactions**: Integration tests should verify the contracts between the application and its infrastructure (e.g., database schema, query correctness, cache serialization).
- **Keep tests independent**: Tests should not depend on each other or on a specific execution order. Each test should set up its own required data.

