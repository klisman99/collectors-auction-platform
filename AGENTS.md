# Project instructions

## Technology-version policy

- Before proposing, documenting, pinning, or upgrading any technology version, verify the current stable release on the technology's official website, documentation, release notes, or official artifact repository.
- Prefer the latest mutually compatible generally available release. Do not select milestones, release candidates, snapshots, early-access builds, or preview releases unless the task explicitly requires them.
- Record the verification date and source links when a decision document contains exact version numbers.
- Let the Spring Boot dependency-management BOM control Spring and supported third-party dependency versions. Add an independent version only when the dependency is outside that BOM or a documented compatibility reason requires it.
- Treat patch and compatible minor upgrades as maintenance changes. Require an ADR for major upgrades or upgrades that change application behavior, data formats, runtime requirements, or architecture.
- Re-run the complete automated test suite and review release notes before accepting dependency upgrades.

## Architecture guardrails

- Build the backend as a single Spring Boot deployable organized by business capability, not by technical layer.
- Keep the module names and domain language aligned with `docs/domain/` and `docs/architecture/modules.md`.
- Enforce module boundaries with Spring Modulith verification tests.
- Keep PostgreSQL as the source of truth. Persist accepted state before acknowledging it or publishing a real-time projection.
- Do not introduce Redis, Kafka, microservices, or Kubernetes without measured evidence and a new ADR.

