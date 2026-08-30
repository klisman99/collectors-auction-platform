# Collectors Auction Platform

A learning-focused, production-minded platform for real-time English auctions of individual physical collectibles.

The project starts as a modular monolith and evolves only through measured architectural changes backed by product or operational evidence. The MVP uses a generic collectible schema rather than category-specific listing models.

## Why this project exists

This is a long-term engineering project for practicing the work expected from a senior backend engineer:

- product discovery and business-rule modeling;
- authentication, authorization, and separation of operational duties;
- upload security and moderated marketplace content;
- concurrency and transactional consistency;
- real-time communication and reconnect recovery;
- idempotency, scheduling, and failure recovery;
- observability and operational readiness;
- explicit technical decision-making through ADRs.

The goal is not to accumulate technologies. Every architectural addition must solve a documented problem and include evidence, trade-offs, and a rollback strategy.

## MVP journey

1. A regular account registers, verifies its simulated email, and submits one collectible.
2. A moderator reviews and approves the item.
3. The seller schedules an English ascending auction.
4. Eligible bidders place concurrent, idempotent bids.
5. Connected clients converge on persisted auction state through REST snapshots and STOMP projections.
6. Late bids extend the effective end time.
7. Administrative suspension and bidder disqualification preserve an auditable history.
8. The system closes the auction and records exactly one outcome.
9. Payment, shipping, delivery, and transactional email are simulated.

## Documentation

- [Domain context](CONTEXT.md)
- [Product vision](docs/product/vision.md)
- [MVP scope](docs/product/scope.md)
- [Business rules](docs/product/business-rules.md)
- [Domain glossary](docs/domain/glossary.md)
- [Use cases](docs/domain/use-cases.md)
- [Domain invariants](docs/domain/invariants.md)
- [State machines](docs/domain/state-machines.md)
- [System context](docs/architecture/system-context.md)
- [Backend modules](docs/architecture/modules.md)
- [Technology stack](docs/architecture/technology-stack.md)
- [Milestones](docs/roadmap/milestones.md)
- [ADR-0001: Start with a modular monolith](docs/adr/0001-start-with-a-modular-monolith.md)
- [ADR-0002: Use Java and Spring Boot](docs/adr/0002-use-java-and-spring-boot.md)

## Current status

M0 product and architecture specification. No application code has been implemented yet. Implementation work is tracked as vertical slices in GitHub Issues.
