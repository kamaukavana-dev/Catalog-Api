# syntax=docker/dockerfile:1.6

# ============================================================
# Stage 1: Build
# Use full JDK for compilation
# ============================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Copy dependency files first for layer caching.
# Dependencies only re-download when pom.xml changes.
COPY pom.xml ./

# Download dependencies (cached layer unless pom.xml changes)
RUN mvn dependency:go-offline -B -q

# Copy source and build
COPY src ./src
RUN mvn -B -DskipTests clean package -q

# ============================================================
# Stage 2: Runtime
# Minimal JRE image, no build tools, smaller attack surface
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user.
# Running as root inside a container is a security violation.
# Even with Docker's namespace isolation, defense-in-depth requires non-root.
RUN addgroup -S catalog && adduser -S catalog -G catalog

WORKDIR /app

COPY --from=build /build/target/catalog-api-*.jar app.jar

# Set ownership before USER switch
RUN chown catalog:catalog app.jar

# Switch to non-root
USER catalog

EXPOSE 8080

# Production JVM flags:
# -XX:+UseContainerSupport: respect cgroup CPU/memory limits (K8s, ECS)
# -XX:MaxRAMPercentage=75.0: use 75% of container RAM for JVM heap
# -XX:+UseG1GC: G1GC is the correct collector for latency-sensitive web APIs
# -XX:MaxGCPauseMillis=200: target max GC pause (not a hard guarantee)
# -XX:+UseStringDeduplication: reduces heap usage for string-heavy apps (catalogs are)
# -Djava.security.egd=file:/dev/./urandom: faster SecureRandom on Linux
# -XX:+HeapDumpOnOutOfMemoryError: capture heap dump before OOM kill
# -XX:HeapDumpPath=/tmp/heap.hprof: save dump to writable location
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:+UseStringDeduplication", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heap.hprof", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
