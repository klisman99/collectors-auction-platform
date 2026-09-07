# Coding standards

This document defines the implementation conventions for this repository. It complements
`AGENTS.md`, `CONTEXT.md`, the domain documents, and the architecture decisions; those
documents take precedence if they conflict with a convention below.

## Before changing code

1. Read `CONTEXT.md`, the relevant module section in `docs/architecture/modules.md`, and
   the affected business rules in `docs/product/business-rules.md`.
2. Keep work limited to the active vertical slice. Modules that are intentionally planned
   but not implemented are roadmap work, not incidental refactoring.
3. Inspect the current working tree before editing. Preserve unrelated user changes.
4. Do not add or update a dependency until its stable GA version and compatibility have
   been checked and recorded as required by `AGENTS.md`.

## Module and domain design

- Organize implementation by business capability. A module owns its entities, repositories,
  application services, migrations, and deliberate public API.
- Do not import internal types or repositories from another module. Cross-module work uses a
  public contract or an immutable after-commit event.
- Controllers adapt HTTP only: validate/translate requests, call one entry-module command or
  query, and map the response. They do not declare transactions or contain business rules.
- Application services own command transactions, authorization decisions, concurrency control,
  and durable state changes. Keep transactions around the command, never a controller
  conversation.
- Put invariant-preserving behavior on the entity or a clearly named domain policy. Do not use
  a primitive or a generic string where a stable domain concept deserves its own type.
- Name classes, methods, variables, and tests after the domain action and outcome. Avoid
  single-letter names except conventional loop indices.

## Java source style

- Use explicit imports; never use wildcard imports.
- Put one annotation, field, statement, parameter, and method declaration on its own readable
  line. Keep lines reasonably short and split fluent calls at semantic boundaries.
- Prefer one top-level production type per file. A tightly coupled package-private request or
  response record may stay beside its controller when that makes the HTTP contract clearer.
- Keep constructors explicit and dependencies `final`. Make visibility as narrow as possible.
- Extract a cohesive operation when a service accumulates unrelated responsibilities. For
  example, catalog image decoding/normalization belongs in a dedicated catalog component,
  separate from draft orchestration.
- Do not duplicate HTTP-problem construction, authorization checks, or state-transition logic.
  Centralize a convention within the owning module without creating a dependency from product
  modules to `platform`.

## HTTP, security, and errors

- REST commands live under `/api/v1`, validate request payloads, and use the HTTP status that
  describes the outcome. Public text is English.
- Every API failure uses Problem Details with `code`, `ruleId`, `fieldErrors`, and `traceId`.
  Business-rule failures reference the matching `BR-*` identifier.
- Authenticate and authorize at the boundary, but repeat ownership-sensitive checks in the
  command path. Never rely on a client-supplied owner or account id.
- Keep sessions server-side. Session creation/replacement that must be serialized with password
  changes belongs to the identity application service, not a transactional controller.
- Treat uploads as hostile: constrain encoded size, verify bytes rather than only declared MIME
  type, reject images above 25 million decoded pixels before allocating their pixel buffer,
  re-encode renditions, and keep object storage private.

## Persistence, events, and concurrency

- PostgreSQL is authoritative. Persist accepted state in the command transaction before an
  acknowledgement or real-time projection.
- Add only forward Flyway migrations. Every table has one owning module; see
  [table ownership](table-ownership.md). Other modules never write or read its tables directly.
- Store money as integer BRL cents and authoritative timestamps as UTC `Instant` values.
- Use database constraints for local invariants. Use PostgreSQL pessimistic locking where the
  domain documents require serialization; prove it with a PostgreSQL Testcontainers test.
- Required notification and audit reactions use immutable, durable after-commit events. Do not
  make listener failure roll back an accepted domain command.

## Tests and completion

- Add a focused regression test for every bug fix and a behavior test for every rule introduced.
- Use unit tests for policies/value objects; use PostgreSQL Testcontainers for mappings,
  migrations, locks, durable event delivery, and concurrency. H2 tests do not prove PostgreSQL
  locking or persistence behavior.
- Keep `ApplicationModules.verify()` passing whenever modules or dependencies change.
- Before handoff, run `./mvnw spotless:check`, the smallest relevant test, then `./mvnw verify`
  when the local environment supports the full suite. Report any infrastructure-limited
  verification clearly. Use `./mvnw spotless:apply` to apply the canonical Java formatting.
