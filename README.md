# Collectors Auction Platform

A learning-focused, production-minded platform for real-time English auctions of modern collectibles.

The first product vertical is collectible trading cards. The project starts as a modular monolith and will evolve through measured architectural changes as new reliability, scale, and product requirements are introduced.

## Why this project exists

This repository is a long-term engineering project for practicing the work expected from a senior backend engineer:

- product discovery and business-rule modeling;
- authentication and authorization;
- concurrency and transactional consistency;
- real-time communication;
- idempotency and failure recovery;
- observability and operational readiness;
- data and architecture migrations;
- explicit technical decision-making through RFCs and ADRs.

The goal is not to accumulate technologies. Every architectural addition must solve a documented problem and include evidence, trade-offs, and a rollback strategy.

## MVP journey

1. A user registers and submits a collectible card.
2. A moderator reviews and approves the item.
3. The seller schedules an English ascending auction.
4. Eligible users place concurrent bids.
5. Accepted bids are broadcast to the auction room in real time.
6. Late bids extend the closing time.
7. The system closes the auction and records exactly one outcome.
8. Payment and shipping are simulated.

## Documentation

- [Product vision](docs/product/vision.md)
- [MVP scope](docs/product/scope.md)
- [Business rules](docs/product/business-rules.md)
- [Domain glossary](docs/domain/glossary.md)
- [Use cases](docs/domain/use-cases.md)
- [Domain invariants](docs/domain/invariants.md)
- [State machines](docs/domain/state-machines.md)
- [System context](docs/architecture/system-context.md)
- [Milestones](docs/roadmap/milestones.md)
- [ADR-0001: Start with a modular monolith](docs/adr/0001-start-with-a-modular-monolith.md)

## Current status

Project foundation and domain discovery. No production code has been implemented yet.
