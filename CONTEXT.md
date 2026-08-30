# Domain context

Collectors Auction Platform is a production-minded learning project for trustworthy real-time English auctions of individual physical collectibles.

The MVP accepts a generic collectible schema across eight broad categories. It simulates email, payment, shipping, delivery, and identity verification so implementation effort can focus on domain modeling, authorization, concurrency, recoverability, real-time projections, auditability, and operational evidence.

## Domain language

Use the canonical vocabulary in `docs/domain/glossary.md`. In particular:

- a **seller** and a **bidder** are contextual relationships of a regular account;
- privileged operational accounts never trade;
- a **collectible item** is one physical object, not a lot;
- an **accepted bid** is immutable, while eligibility can later be changed by an append-only disqualification;
- an **effective end time** includes late-bid extensions and suspension recovery;
- a **sale** is created only by a sold auction outcome and owns the simulated settlement lifecycle.

## Sources of truth

- Product intent and scope: `docs/product/`
- Domain vocabulary, use cases, invariants, and state machines: `docs/domain/`
- System boundaries and modules: `docs/architecture/`
- Architectural decisions: `docs/adr/`
- Delivery order: `docs/roadmap/milestones.md`

Business-rule identifiers in `docs/product/business-rules.md` must be referenced by implementation issues, tests, API errors, and future design documents.
