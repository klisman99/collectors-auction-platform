# ADR-0001: Start with a modular monolith

- **Status:** Accepted
- **Date:** 2026-08-28

## Context

The product is at the discovery stage. Its highest risks are misunderstood auction rules, concurrency bugs, and an incorrect domain model—not independent service deployment.

Starting with microservices would introduce network failure modes, distributed transactions, deployment coordination, and duplicated operational setup before the module boundaries are validated.

The project still needs architecture boundaries that can evolve later.

## Decision

Implement the MVP as one deployable backend organized into explicit domain modules:

- identity;
- catalog;
- moderation;
- auctions;
- bidding;
- settlement;
- notifications;
- audit.

Modules own their behavior and expose deliberate interfaces. Database access must not become an unrestricted shared utility across the codebase.

Use a single relational database as the source of truth for the MVP. Real-time messages are emitted only after the corresponding state is durable.

## Consequences

### Positive

- Faster end-to-end product learning.
- Local transactions for the first concurrency model.
- Simpler development, testing, deployment, and debugging.
- Lower operational cost.
- Module boundaries can be evaluated with real use cases.

### Negative

- All modules share one deployment lifecycle.
- A poorly enforced module boundary can degrade into a tightly coupled monolith.
- Scaling one hot path independently is not initially available.

## Evolution triggers

A service extraction will require evidence such as:

- the bidding path needs an independent scaling or availability profile;
- releases are blocked by module ownership or deployment coupling;
- a module requires a different data ownership or consistency model;
- measured resource contention cannot be resolved inside the deployment;
- a deliberate migration exercise is approved through an RFC.

Technology interest alone is not an extraction trigger.
