# Backend modules

The modular monolith is organized by business capability. Each module owns its domain behavior, application services, persistence model, and public module API. Technical layers live inside a module rather than becoming application-wide packages.

## Dependency direction

~~~mermaid
flowchart LR
    Identity[identity]
    Catalog[catalog]
    Moderation[moderation]
    Auctions[auctions]
    Bidding[bidding]
    Settlement[settlement]
    Notifications[notifications]
    Audit[audit]

    Moderation --> Identity
    Moderation --> Catalog
    Auctions --> Identity
    Auctions --> Catalog
    Bidding --> Identity
    Bidding --> Auctions
    Settlement --> Auctions
    Notifications -. consumes events .-> Auctions
    Notifications -. consumes events .-> Bidding
    Notifications -. consumes events .-> Settlement
    Audit -. consumes events .-> Identity
    Audit -. consumes events .-> Moderation
    Audit -. consumes events .-> Auctions
    Audit -. consumes events .-> Settlement
~~~

Dashed dependencies represent reactions to published application events. The final allowed dependency graph must be encoded in Spring Modulith declarations and verified in tests.

## Module responsibilities

### identity

Owns accounts, credentials, roles, session lifecycle, and suspension. Exposes actor identity, account status, and authorization-relevant queries without exposing credential persistence.

### catalog

Owns collectible drafts, trading-card attributes, images, ownership, review submission status, and immutable item snapshot creation.

### moderation

Owns moderation decisions and privileged review workflows. It uses catalog commands and publishes auditable decisions; it does not write catalog tables directly.

### auctions

Owns auction configuration, lifecycle, effective end time, reserve evaluation, seller-decision deadline, and the final outcome. It references immutable item and seller snapshots or stable identifiers exposed by owning modules.

### bidding

Owns bid commands, idempotency results, accepted bids, rejected-attempt records, server acceptance ordering, and the transactional contention strategy. It may change auction bidding state only through the auctions module's deliberate API.

### settlement

Owns the exactly-one sale created from a sold auction and the simulated payment, shipping, delivery, and completion lifecycle.

### notifications

Projects committed events into simulated email and live-client notifications. A delivery failure cannot roll back an already accepted bid or auction outcome.

### audit

Stores append-only records for privileged and terminal actions. It consumes explicit facts from other modules and must not become a generic debug-log table.

## Interaction rules

- Controllers call the application API of exactly one entry module.
- A module never imports another module's internal package or repository.
- Cross-module calls use public Java types designed as contracts, or immutable application events after commit.
- Domain entities do not cross module boundaries.
- A shared package is limited to stable, behavior-free primitives whose meaning is truly universal. Convenience code stays with its owning module.
- Cycles are prohibited and fail the architecture test.
- Direct database reads across another module's tables are prohibited, including for “simple” reporting, until an explicit read-model decision is recorded.

