# System context

This document describes system boundaries, not detailed implementation packages.

## Context

~~~mermaid
flowchart LR
    Visitor[Visitor] --> Platform[Collectors Auction Platform]
    Seller[Seller] --> Platform
    Bidder[Bidder] --> Platform
    Moderator[Moderator] --> Platform
    Administrator[Administrator] --> Platform
    Platform --> Email[Mailpit simulated email]
    Platform --> Objects[MinIO object storage]
    Platform --> Payment[Simulated payment]
    Platform --> Shipping[Simulated shipping]
    Platform --> Observability[Optional local observability stack]
~~~

## Responsibilities inside the platform

- Regular and operational account identity, verification, authorization, sessions, and suspension.
- Generic collectible catalog, managed images, ownership declaration, and moderation.
- Public auction discovery, scheduling, lifecycle, and immutable published snapshots.
- Concurrent bid validation, idempotency, sequencing, eligibility, and disqualification.
- REST snapshots and public read-only STOMP auction-room updates.
- Deadline reconciliation, closing, reserve decision, and exactly-one outcome selection.
- Simulated settlement and transactional email.
- Layered audit history and operational observability.

## Initial technical direction

The MVP uses a modular monolith with explicit backend boundaries:

- identity;
- catalog;
- moderation;
- auctions;
- bidding;
- settlement;
- notifications;
- audit.

The backend is one executable Spring Boot application and one PostgreSQL database. The React SPA is built separately and exposed under the same browser origin as `/api` and the STOMP endpoint through a reverse proxy.

PostgreSQL is the source of truth. MinIO stores private managed media. Mailpit provides local simulated email. Real-time messages and internal listeners project already committed facts and never decide whether a bid, transition, or outcome is valid.

Kafka, Redis, microservices, Kubernetes, external payment, and public-cloud services are intentionally not initial assumptions.

ADR-0002 selects Java and Spring Boot for the deployable backend. The concrete baseline is maintained in [Technology stack](technology-stack.md), and the initial ownership and dependency rules are defined in [Backend modules](modules.md).

## Security and privacy boundaries

- Session cookies are HttpOnly, Secure outside local development, and SameSite; state-changing HTTP requests require CSRF protection.
- Visitors may read public auction data and subscribe to public STOMP topics but cannot send domain commands over STOMP.
- Draft media, exact reserves, persistent bidder identity, and internal audit notes are authorization-protected.
- Operational accounts are dedicated and cannot participate in marketplace transactions.

## Quality goals

1. Correct auction outcomes under concurrency and administrative exceptions.
2. Safe retries through idempotent commands and durable background work.
3. Recoverability after process restart and listener failure.
4. Clear, versioned, and testable business rules.
5. Observable failures, deadlines, and privileged decisions.
6. Public privacy without sacrificing auction transparency.
7. Evolution without prematurely distributing the system.
