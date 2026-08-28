# Backend technology stack

This document turns ADR-0002 into an implementation baseline. Exact versions were verified against official sources on 2026-08-28 and must be rechecked before they are changed.

## Baseline

| Concern | Choice | Baseline | Ownership |
| --- | --- | --- | --- |
| Language and runtime | Java | 25 LTS | Explicit project version |
| Application framework | Spring Boot | 4.1.1 | Spring Boot parent/BOM |
| Module verification | Spring Modulith | 2.1.1 | Spring Modulith BOM compatible with Boot 4.1 |
| Build | Maven Wrapper | Maven 3.9.16 | Wrapper configuration |
| Database | PostgreSQL | 18.6 | Deployment image and Testcontainers |
| HTTP | Spring MVC | Boot-managed | Spring Boot BOM |
| Authentication and authorization | Spring Security | Boot-managed | Spring Boot BOM |
| Persistence | Spring Data JPA and PostgreSQL JDBC | Boot-managed | Spring Boot BOM |
| Database migrations | Flyway | Boot-managed | Spring Boot BOM |
| Real-time projection | Spring WebSocket | Boot-managed | Spring Boot BOM |
| Validation | Jakarta Validation | Boot-managed | Spring Boot BOM |
| Operations | Actuator and Micrometer | Boot-managed | Spring Boot BOM |
| Testing | JUnit, Spring Boot Test, Spring Modulith Test, Testcontainers | Boot/Modulith-managed where available | Managed BOMs |

The `pom.xml` must avoid restating versions already managed by Spring Boot or Spring Modulith.

## Application shape

- One Maven application and one executable Spring Boot JAR.
- One root Java package with direct subpackages representing application modules.
- One PostgreSQL database, with table ownership documented per module.
- REST/JSON commands and queries. Errors use stable business-rule codes and Problem Details responses.
- WebSocket messages project already committed auction state; they never decide whether a bid was accepted.
- Background work claims due operations from durable database state so a process restart does not lose auction closing.

## Persistence rules

- Use transactions around business commands, not controller conversations.
- Store money as integer BRL cents in the domain and `BIGINT` in PostgreSQL.
- Store authoritative instants in UTC and convert only at system boundaries.
- Use database constraints for invariants that can be expressed locally.
- Use optimistic or pessimistic concurrency deliberately; the bidding strategy must be demonstrated by concurrent integration tests before it is considered complete.
- Keep Flyway migrations forward-only after they reach a shared environment.

## Test baseline

- Plain unit tests for value objects, policies, and state transitions.
- Module tests for use cases and legal module interaction.
- PostgreSQL Testcontainers integration tests for repositories, migrations, locking, idempotency, and closing.
- Concurrency tests that start competing commands together and assert durable outcomes, not only HTTP responses.
- Architecture tests that run `ApplicationModules.verify()`.
- End-to-end tests for the MVP journey only after the inner test layers are useful.

## Dependency update workflow

1. Check the official project page, system requirements, compatibility matrix, and release notes.
2. Select only a stable GA release compatible with the runtime and Spring Boot line.
3. Update the wrapper, parent, BOM, or deployment image that owns the version.
4. Run unit, module, integration, concurrency, and architecture tests.
5. Review schema, serialization, security, and observability changes.
6. Record the verification date and rollback approach in the pull request; create an ADR if the change is architecturally significant.

