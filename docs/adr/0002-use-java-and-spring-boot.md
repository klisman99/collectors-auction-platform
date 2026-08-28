# ADR-0002: Use Java and Spring Boot for the modular monolith

- **Status:** Accepted
- **Date:** 2026-08-28

## Context

ADR-0001 selects a modular monolith but intentionally leaves the implementation technology open. The backend now needs a concrete runtime, application framework, build tool, persistence baseline, and a way to keep its business modules structurally visible.

The technology choice must support transactional consistency for concurrent bids, server-authoritative scheduling, session security, real-time projections, observability, integration testing, and later architectural evolution without requiring distributed infrastructure for the MVP.

## Decision

Implement the backend in Java using the latest mutually compatible stable releases verified on 2026-08-28:

- Java 25 LTS;
- Spring Boot 4.1.1;
- Spring Modulith 2.1.1;
- Apache Maven 3.9.16 through the Maven Wrapper;
- PostgreSQL 18.6.

Use Spring Boot's dependency management for Spring Framework, Spring Security, Spring Data, the servlet container, Jackson, validation, testing, and other managed libraries. Independent dependency versions must not be copied into the build without a compatibility reason.

Organize the code as business modules below one application root package. Spring Modulith verification tests will make illegal module dependencies fail in the build. The initial module map is defined in `docs/architecture/modules.md`.

Use:

- Spring MVC for the HTTP API;
- Spring Security with server-side, revocable sessions;
- Spring Data JPA for ordinary aggregate persistence, with explicit PostgreSQL locking or SQL where the bidding concurrency model requires it;
- Flyway for forward-only schema migrations;
- Spring WebSocket for live auction projections while HTTP remains the command and snapshot authority;
- Spring Boot Actuator and Micrometer for the operational baseline;
- JUnit, Spring Boot Test, Spring Modulith Test, and Testcontainers with real PostgreSQL for integration and concurrency tests.

The application is packaged as one executable JAR and uses one PostgreSQL database. A single deployable does not permit unrestricted cross-module repository access.

## Why Java 25 instead of Java 26

Java 26 is a newer feature release, but Java 25 is the current LTS release. The platform favors the longer support horizon for a production-minded learning project. Spring Boot 4.1.1 supports both, so this is a lifecycle choice rather than a framework constraint.

## Consequences

### Positive

- Mature transaction, security, validation, testing, and observability support.
- A stable LTS runtime compatible with the current Spring Boot generation.
- Automated verification of modular boundaries.
- Real PostgreSQL integration tests can exercise locking and transactional behavior.
- One dependency-management authority reduces incompatible library combinations.

### Negative

- The team must understand Spring transaction boundaries and proxy behavior.
- JPA abstractions can hide SQL and locking costs unless critical paths are measured and inspected.
- Spring Modulith verifies structural rules but cannot repair a poorly modeled domain.
- Major Spring Boot upgrades may require coordinated Jakarta and ecosystem migrations.

## Version policy

“Latest” means the newest mutually compatible generally available release, not a preview. Exact versions are a reviewed baseline, not permission for automatic untested upgrades.

- Verify official sources again before scaffolding or upgrading the application.
- Accept compatible patch and minor upgrades through a tested maintenance change.
- Use a new ADR for major runtime, framework, database, or build-tool upgrades.
- Review release notes, run all tests, and retain a rollback path for every upgrade.

## Sources verified on 2026-08-28

- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Spring Boot project and stable releases](https://spring.io/projects/spring-boot/)
- [Spring Boot 4.1 system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Modulith project and stable releases](https://spring.io/projects/spring-modulith/)
- [Apache Maven downloads](https://maven.apache.org/download.cgi)
- [PostgreSQL versioning policy and supported releases](https://www.postgresql.org/support/versioning/)

