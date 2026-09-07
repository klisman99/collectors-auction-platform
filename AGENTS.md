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
- Keep module names and domain language aligned with `CONTEXT.md`, `docs/domain/`, and `docs/architecture/modules.md`.
- Enforce module boundaries with Spring Modulith verification tests.
- Keep PostgreSQL as the source of truth. Persist accepted state before acknowledging it or publishing a real-time projection.
- Do not introduce Redis, Kafka, microservices, or Kubernetes without measured evidence and a new ADR.

## Code quality

- Follow `docs/architecture/coding-standards.md` for implementation, error handling, persistence,
  security, and verification conventions.
- Keep `docs/architecture/table-ownership.md` current whenever a migration adds or changes a table.

## Agent skills

### Issue tracker

Work is tracked in GitHub Issues for `klisman99/collectors-auction-platform`. See `docs/agents/issue-tracker.md`.

### Triage labels

This repository intentionally does not use triage-state labels. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repository rooted at `CONTEXT.md`, with ADRs in `docs/adr/`. See `docs/agents/domain.md`.
