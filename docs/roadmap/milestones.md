# Roadmap and milestones

Dates are intentionally omitted until weekly capacity is known. Every milestone ends with a demonstrable vertical product increment and includes the minimal English-language UI needed to exercise it.

## M0 — Product and architecture specification

**Outcome:** one decision-complete source of truth and an executable GitHub backlog.

- Generic collectible vision, scope, rules, glossary, use cases, invariants, and state machines.
- Accepted modular-monolith and Java/Spring ADRs.
- Module, technology, local-operation, and agent-workflow documentation.
- Nineteen AFK vertical-slice issues with acceptance criteria and real dependencies.

## M1 — Identity, collectible submission, and moderation

**Outcome:** the local platform runs, a regular account verifies and authenticates, submits a safe generic collectible, and receives a moderation decision.

1. [#26 — Launch the executable platform baseline](https://github.com/klisman99/collectors-auction-platform/issues/26).
2. [#27 — Register, verify, and sign in](https://github.com/klisman99/collectors-auction-platform/issues/27).
3. [#28 — Recover credentials and manage sessions](https://github.com/klisman99/collectors-auction-platform/issues/28).
4. [#29 — Invite and deactivate operational accounts](https://github.com/klisman99/collectors-auction-platform/issues/29).
5. [#30 — Create a generic collectible draft with processed images](https://github.com/klisman99/collectors-auction-platform/issues/30).
6. [#31 — Submit and moderate a collectible](https://github.com/klisman99/collectors-auction-platform/issues/31).

## M2 — Auction lifecycle and discovery

**Outcome:** an approved collectible can be scheduled, discovered publicly, started, suspended, resumed, or cancelled under explicit rules.

7. [#32 — Schedule an approved collectible for auction](https://github.com/klisman99/collectors-auction-platform/issues/32).
8. [#33 — Reschedule, start, cancel, and publicly browse auctions](https://github.com/klisman99/collectors-auction-platform/issues/33).
9. [#34 — Suspend, resume, and administratively cancel auctions](https://github.com/klisman99/collectors-auction-platform/issues/34).

## M3 — Correct bidding and closing engine

**Outcome:** competing bidders, administrative disqualification, deadlines, and closing produce one durable eligible-bid order and outcome.

10. [#35 — Place idempotent bids under concurrency](https://github.com/klisman99/collectors-auction-platform/issues/35).
11. [#36 — Extend the effective end time for late bids](https://github.com/klisman99/collectors-auction-platform/issues/36).
12. [#37 — Suspend accounts and disqualify bids across auctions](https://github.com/klisman99/collectors-auction-platform/issues/37).
13. [#38 — Close every auction exactly once](https://github.com/klisman99/collectors-auction-platform/issues/38).

## M4 — Real-time auction room

**Outcome:** visitors and bidders see committed state changes and recover from missed or duplicated messages without making STOMP authoritative.

14. [#39 — Project live auction state over STOMP and recover after reconnect](https://github.com/klisman99/collectors-auction-platform/issues/39).

## M5 — Reserve decision, settlement, and history

**Outcome:** every auction reaches an explicit result, sold auctions complete or fail simulated settlement, and actors see the correct history.

15. [#40 — Decide a below-reserve final offer](https://github.com/klisman99/collectors-auction-platform/issues/40).
16. [#41 — Simulate payment and shipment for a sold item](https://github.com/klisman99/collectors-auction-platform/issues/41).
17. [#42 — Complete or fail settlement and release eligible items](https://github.com/klisman99/collectors-auction-platform/issues/42).
18. [#43 — Browse personal history and layered audit timelines](https://github.com/klisman99/collectors-auction-platform/issues/43).

## M6 — Reproducible operational proof

**Outcome:** the complete MVP journey is locally deployable, measurable, recoverable, and supported by automated evidence.

19. [#44 — Prove the reproducible MVP journey](https://github.com/klisman99/collectors-auction-platform/issues/44).

The proof includes restart experiments, durable listener recovery, security checks, OpenAPI/client drift checks, 100-bidder concurrency, p95 bid responses below 500 ms, live events below one second, dashboards, traces, runbooks, and a postmortem exercise.

## Later evolution candidates

These are not commitments. Each requires an RFC and evidence.

- Category-specific schemas over the generic item core.
- Search, filters, watchlists, and automatic proxy bidding.
- Reputation, authenticity, and dispute workflows.
- Feature-flag control plane and experimentation.
- Transactional outbox and external event streaming when Modulith delivery is insufficient.
- Horizontal bidding partitions and deliberate service extraction.
- Multi-tenant auction houses, cloud deployment, and Kubernetes.
