# Technology stack

This document turns ADR-0002 into an implementation baseline. Exact backend versions were verified against official sources on 2026-08-30 and must be checked again before scaffolding or upgrading.

## Backend baseline

| Concern | Choice | Baseline | Ownership |
| --- | --- | --- | --- |
| Language and runtime | Java | 25 LTS | Explicit project version |
| Application framework | Spring Boot | 4.1.1 | Spring Boot parent/BOM |
| Module verification and durable events | Spring Modulith | 2.1.1 | Compatible Modulith BOM |
| Build | Maven Wrapper | Maven 3.9.16 | Wrapper configuration |
| Database | PostgreSQL | 18.6 | Deployment image and Testcontainers |
| HTTP and validation | Spring MVC and Jakarta Validation | Boot-managed | Spring Boot BOM |
| Authentication and authorization | Spring Security server-side sessions | Boot-managed | Spring Boot BOM |
| Persistence and migrations | Spring Data JPA, PostgreSQL JDBC, Flyway | Boot-managed | Spring Boot BOM |
| Realtime | Spring WebSocket with STOMP simple broker | Boot-managed | Spring Boot BOM |
| Operations | Actuator, Micrometer, OpenTelemetry | Boot-managed where available | Reviewed integration |
| Testing | JUnit, Spring Boot Test, Modulith Test, Testcontainers | BOM-managed where available | Build configuration |

The build must not restate versions already managed by Spring Boot or Spring Modulith.

## Frontend baseline

- React with TypeScript and Vite.
- Tailwind CSS plus accessible headless component primitives.
- Generated TypeScript types and client from backend-generated OpenAPI.
- REST for commands and authoritative snapshots.
- STOMP over WebSocket for committed public projections; reconnect always reloads REST state.
- User-facing text in English, BRL formatting, and `America/Sao_Paulo` display time.

Exact frontend dependency versions are selected from mutually compatible stable GA releases when the first executable slice is scaffolded and are controlled by the lockfile.

## Application shape

- One Maven backend application and executable Spring Boot JAR.
- One Vite SPA exposed under the same browser origin through a reverse proxy.
- One PostgreSQL database with documented table ownership per module.
- One private MinIO bucket set for original normalized images and thumbnails.
- REST/JSON under `/api/v1`; stable errors use Problem Details with `code`, `ruleId`, `fieldErrors`, and `traceId`.
- Session cookies are HttpOnly and SameSite; production-like profiles set Secure; mutating requests use CSRF tokens.
- Public read-only STOMP subscriptions share the same origin; domain commands remain HTTP-only.
- Background work claims durable due records so restart cannot lose start, closing, decision, settlement, notification, or audit work.

## Local and operational stack

The default Docker Compose environment contains PostgreSQL, MinIO, and Mailpit. An optional observability profile adds Prometheus, Grafana, and Tempo. The application emits structured JSON logs, Micrometer metrics, and OpenTelemetry traces.

GitHub Actions validates documentation, backend and frontend builds, tests, generated OpenAPI/client drift, container images, and the reproducible local packaging. A public deployment is not required for the MVP.

## Persistence and concurrency rules

- Store money as integer BRL cents in the domain and `BIGINT` in PostgreSQL.
- Store authoritative instants in UTC and convert only at system boundaries.
- Use transactions around business commands, not controller conversations.
- Acquire a pessimistic PostgreSQL row lock for the auction bidding state before bid validation.
- Use database constraints for local invariants such as idempotency and unique per-auction sequences.
- Persist Spring Modulith event publications in the originating transaction for required after-commit listeners.
- Keep Flyway migrations forward-only after they reach a shared environment.

## Test baseline

- Unit tests for value objects, policies, authorization, and transitions.
- Module tests for use cases and legal interactions.
- PostgreSQL Testcontainers tests for mappings, migrations, locks, idempotency, durable events, scheduling, and closing.
- Concurrency tests that start competing commands together and assert durable results.
- Architecture tests that run `ApplicationModules.verify()`.
- Frontend component and contract tests plus end-to-end tests for each completed vertical slice.
- Final load evidence for 100 concurrent bidders, p95 bid response below 500 ms, and live event below one second without loss or duplication.

## Version sources verified 2026-08-30

- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Spring Boot stable documentation](https://docs.spring.io/spring-boot/)
- [Spring Modulith project](https://spring.io/projects/spring-modulith/)
- [Apache Maven downloads](https://maven.apache.org/download.cgi)
- [PostgreSQL versioning policy](https://www.postgresql.org/support/versioning/)

Additional first-slice dependencies were verified against official sources on 2026-08-31:

- [Spring Boot Flyway guidance](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Spring Boot OpenTelemetry tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [springdoc compatibility and stable release](https://springdoc.org/)
- [Hey API OpenAPI TypeScript generator](https://www.npmjs.com/package/@hey-api/openapi-ts)
- [NGINX official container tags](https://hub.docker.com/_/nginx)
- [MinIO server release and final official container tag](https://github.com/minio/minio/releases/tag/RELEASE.2025-09-07T16-13-09Z)
- [MinIO client release and official container tag](https://github.com/minio/mc/releases/tag/RELEASE.2025-08-13T08-35-41Z)

## Dependency update workflow

1. Check official project pages, system requirements, compatibility matrices, and release notes.
2. Select only a stable GA release compatible with Java, Spring Boot, and the surrounding toolchain.
3. Update the wrapper, parent, BOM, lockfile, or deployment image that owns the version.
4. Run unit, module, integration, concurrency, architecture, frontend, and end-to-end tests.
5. Review schema, serialization, security, observability, and generated-contract changes.
6. Record verification date and rollback approach; create an ADR for architecturally significant change.
