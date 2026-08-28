# System context

This document describes system boundaries, not the final implementation architecture.

## Context

~~~mermaid
flowchart LR
    Seller[Seller] --> Platform[Collectors Auction Platform]
    Bidder[Bidder] --> Platform
    Moderator[Moderator] --> Platform
    Platform --> Email[Simulated email]
    Platform --> Payment[Simulated payment]
    Platform --> Shipping[Simulated shipping]
~~~

## Responsibilities inside the platform

- Account authentication and session management.
- Collectible catalog and moderation.
- Auction scheduling and lifecycle.
- Concurrent bid validation and acceptance.
- Real-time auction-room updates.
- Closing and outcome selection.
- Simulated settlement.
- Audit history and operational observability.

## Initial technical direction

The MVP will use a modular monolith with explicit internal boundaries:

- identity;
- catalog;
- moderation;
- auctions;
- bidding;
- settlement;
- notifications;
- audit.

The database is the source of truth. Real-time delivery is a projection of persisted state.

Technology choices beyond this boundary will be recorded in separate ADRs. Kafka, Redis, microservices, and Kubernetes are intentionally not initial assumptions.

ADR-0002 selects Java and Spring Boot for the deployable backend. The concrete baseline is maintained in [Backend technology stack](technology-stack.md), and the initial ownership and dependency rules are defined in [Backend modules](modules.md).

## Quality goals

1. Correct auction outcomes under concurrency.
2. Safe retries through idempotent commands.
3. Recoverability after process restart.
4. Clear and testable business rules.
5. Observable failures and decisions.
6. Evolution without rewriting the product blindly.
