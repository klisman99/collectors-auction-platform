# ADR-0002: Use Java and Spring Boot for the modular monolith

- **Status:** Accepted
- **Date:** 2026-08-30

## Context

ADR-0001 selects a modular monolith but leaves implementation technology open. The backend needs a concrete runtime, application framework, build tool, persistence baseline, and automated module-boundary verification.

The stack must support transactional consistency for concurrent bids, durable scheduling, server-side session security, managed image metadata, REST snapshots, STOMP projections, durable internal event publication, observability, and real PostgreSQL integration tests without requiring distributed infrastructure.

## Decision

Implement the backend with the latest mutually compatible stable releases verified on 2026-08-30:

- Java 25 LTS;
- Spring Boot 4.1.1;
- Spring Modulith 2.1.1;
- Apache Maven 3.9.16 through the Maven Wrapper;
- PostgreSQL 18.6.

Use Spring Boot dependency management for Spring Framework, Security, Data, MVC, WebSocket, validation, testing, and managed third-party libraries. Use Spring Modulith to verify business-module boundaries and persist required event publications for retryable after-commit audit and notification listeners.

Use:

- Spring MVC for `/api/v1` REST commands, queries, OpenAPI generation, and Problem Details errors;
- Spring Security with revocable server-side sessions and CSRF protection;
- Spring Data JPA for ordinary aggregate persistence, with explicit PostgreSQL locking or SQL on the bidding path;
- Flyway for forward-only schema migrations;
- Spring WebSocket with STOMP and the in-memory simple broker for read-only client projections;
- Actuator, Micrometer, and OpenTelemetry for the operational baseline;
- JUnit, Spring Boot Test, Spring Modulith Test, and Testcontainers with PostgreSQL for verification.

The backend is packaged as one executable JAR and uses one PostgreSQL database. A single deployable does not permit unrestricted cross-module repository or table access.

## Why Java 25 instead of Java 26

Java 26 is a newer feature release, while Java 25 is the current LTS. The project favors the longer support horizon for a production-minded learning baseline. This is a lifecycle choice, not permission to ignore future security updates.

## Consequences

### Positive

- Mature transaction, security, validation, scheduling, testing, and observability support.
- A stable LTS runtime and current stable Spring generation.
- Automated verification of modular boundaries.
- Durable local event publication without Kafka.
- Real PostgreSQL tests can exercise locks and transactional behavior.
- One dependency-management authority reduces incompatible library combinations.

### Negative

- The team must understand Spring transaction boundaries, session security, and proxy behavior.
- JPA can hide SQL and locking costs unless critical paths are measured and inspected.
- Modulith verifies structure but cannot repair poor domain boundaries.
- Major framework upgrades may require coordinated Jakarta, persistence, and serialization migrations.

## Version policy

"Latest" means newest mutually compatible generally available release, never preview or snapshot. Exact versions are a reviewed baseline, not permission for automatic untested upgrades.

- Verify official sources again immediately before scaffolding or upgrading.
- Accept compatible patch and minor upgrades through a tested maintenance change.
- Use a new ADR for major runtime, framework, database, or build-tool upgrades.
- Review release notes, run the complete suite, and retain a rollback path.

## Sources verified 2026-08-30

- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Spring Boot stable documentation](https://docs.spring.io/spring-boot/)
- [Spring Modulith project](https://spring.io/projects/spring-modulith/)
- [Apache Maven downloads](https://maven.apache.org/download.cgi)
- [PostgreSQL versioning policy](https://www.postgresql.org/support/versioning/)
