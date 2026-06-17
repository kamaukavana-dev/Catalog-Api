package com.catalog.common;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for {@code @SpringBootTest} integration tests.
 *
 * <p>Uses the Testcontainers <em>singleton container</em> pattern: the PostgreSQL
 * container is declared as a {@code static} field and started once from a static
 * initializer. It is intentionally <strong>not</strong> annotated with
 * {@code @Testcontainers}/{@code @Container}, because that extension starts and stops
 * the static container around <em>every</em> test class. Combined with Spring's
 * context cache (all IT classes share an identical configuration and therefore one
 * cached {@code ApplicationContext}), per-class stop/start would rebind the database
 * to a fresh mapped port while the cached context kept pointing at the old, now-dead
 * port — producing "connection refused" on every IT class after the first.
 *
 * <p>By starting the container manually and never stopping it, the single container
 * outlives all test classes (the Testcontainers Ryuk reaper removes it on JVM exit),
 * so the cached context's {@code @ServiceConnection} binding stays valid throughout.
 *
 * <p>Because the database is now shared across every IT class, {@link #resetDatabase()}
 * truncates all application tables before each test so committed state from one test
 * (these ITs commit for real — they exercise optimistic locking and concurrency, so
 * transactional rollback is not an option) cannot leak into the next. {@code CASCADE}
 * makes the truncation FK-safe regardless of table order; {@code flyway_schema_history}
 * is preserved so migrations are not re-run. This base {@code @BeforeEach} runs before
 * each subclass {@code @BeforeEach}, so subclasses see an empty schema to populate.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                        + "AND tablename <> 'flyway_schema_history'",
                String.class);
        if (tables.isEmpty()) {
            return;
        }
        String joined = tables.stream()
                .map(t -> "\"" + t + "\"")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("TRUNCATE TABLE " + joined + " RESTART IDENTITY CASCADE");
    }
}
