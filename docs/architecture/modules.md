# Backend modules

The modular monolith is organized by business capability. Each module owns its behavior, application services, persistence model, and public module API. Technical layers live inside a module rather than becoming application-wide packages.

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
    Platform[platform]

    Moderation --> Identity
    Moderation --> Catalog
    Auctions --> Identity
    Auctions --> Catalog
    Bidding --> Identity
    Bidding --> Auctions
    Settlement --> Identity
    Settlement --> Auctions
    Notifications -. durable events .-> Identity
    Notifications -. durable events .-> Moderation
    Notifications -. durable events .-> Auctions
    Notifications -. durable events .-> Bidding
    Notifications -. durable events .-> Settlement
    Audit -. durable events .-> Identity
    Audit -. durable events .-> Moderation
    Audit -. durable events .-> Auctions
    Audit -. durable events .-> Bidding
    Audit -. durable events .-> Settlement
~~~

Dashed dependencies represent reactions to durable after-commit application events. The final allowed graph must be encoded in Spring Modulith declarations and verified in tests.

`platform` is the dependency-free application-shell capability. It owns cross-cutting HTTP entry conventions, security configuration, status/OpenAPI contracts, and operational instrumentation. Product modules must not depend on it.

## Module responsibilities

### identity

Owns regular and operational accounts, normalized email, public handle, credentials, verification and recovery tokens, roles, session lifecycle, invitations, account status, and rate-limit identity keys. It exposes authorization-relevant facts without exposing credential persistence.

### catalog

Owns generic collectible drafts, category, condition, ownership declaration, managed image ordering and metadata, item lifecycle, auction lock, relisting eligibility, archival, and immutable item snapshot creation. MinIO is accessed through a catalog-owned storage port.

### moderation

Owns review decisions, prohibited-content policy, public reasons, and internal notes. It invokes catalog's deliberate commands and publishes auditable facts; it never writes catalog tables directly.

### auctions

Owns auction configuration, published policy and item snapshots, public discovery, lifecycle, suspension timing, effective end, reserve evaluation, seller-decision deadline, closing, and final outcome. Durable database deadlines drive automatic work and restart reconciliation.

### bidding

Owns bid attempts, idempotency results, accepted bids, auction-local sequence, pseudonyms, disqualifications, eligible-leader projection, and the pessimistic PostgreSQL contention strategy. It changes auction bidding state only through deliberate auctions-module APIs.

### settlement

Owns the exactly-one sale created from a sold auction and the simulated payment, shipment, delivery confirmation, expiry, completion, failure, and item-release lifecycle.

### notifications

Projects durable committed events into Mailpit transactional email and STOMP client updates. Delivery failure cannot roll back domain state and is retried through Spring Modulith's event publication registry.

### audit

Stores append-only domain and administrative facts and produces public, participant, moderator, and administrator projections. It must not become a generic debug-log table.

### platform

Owns the same-origin application shell: status and OpenAPI endpoints, request tracing, stable HTTP errors, session/CSRF configuration, and operational wiring. It contains technical adapters only inside this explicit module and has no dependency on product modules.

## Interaction rules

- An HTTP controller calls the application API of exactly one entry module.
- A STOMP inbound frame never executes a domain command; realtime is read-only projection.
- A module never imports another module's internal package or repository.
- Cross-module calls use deliberate public Java contracts or immutable after-commit events.
- Domain entities do not cross module boundaries.
- A shared package is limited to stable behavior-free primitives whose meaning is universal.
- Cycles and direct cross-module table reads are prohibited and fail architecture tests.
- Internal event publications required for audit or notification are stored durably in the originating transaction.
