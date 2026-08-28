# Roadmap and milestones

Dates are intentionally omitted until the weekly capacity is known. Each milestone should end with a demonstrable product increment.

## M0 — Project foundation

**Outcome:** shared product language and an executable backlog.

- Product vision and MVP scope.
- Business rules and invariants.
- State machines and system context.
- Initial ADR.
- Epics and prioritized tasks.

## M1 — Identity and collectible submission

**Outcome:** a user can authenticate, submit a trading card, and receive a moderation decision.

- Account registration and sessions.
- Account suspension.
- Card draft and required attributes.
- Item submission.
- Moderator approval and rejection.
- Audit events for privileged actions.

## M2 — Auction lifecycle

**Outcome:** an approved card can be scheduled and become live.

- Auction draft and configuration.
- Immutable item snapshot.
- Start and effective end times.
- Reserve and increment rules.
- Cancellation and suspension.
- Lifecycle transition tests.

## M3 — Correct bidding engine

**Outcome:** competing bidders produce one durable accepted-bid order.

- Bid command and error model.
- Idempotency.
- Transactional concurrency control.
- Minimum increment validation.
- Seller exclusion.
- Closing protection.
- Atomic and idempotent closing.
- Concurrent integration tests.

## M4 — Real-time auction room

**Outcome:** multiple clients see accepted bids and deadline changes without making WebSocket delivery authoritative.

- Initial auction snapshot.
- Authenticated live connection.
- Bid and deadline events.
- Reconnection and state recovery.
- React auction room.
- Multi-client demo.

## M5 — Outcome and simulated settlement

**Outcome:** every auction reaches an explicit result and sold auctions complete a simulated sale.

- Reserve decision.
- Seller decision deadline.
- Exactly-one-sale guarantee.
- Simulated payment.
- Simulated shipment and delivery.
- Buyer and seller history.

## M6 — Operational baseline

**Outcome:** the MVP is deployable, measurable, and recoverable.

- Structured logs, metrics, and traces.
- Service-level indicators.
- Load and concurrency tests.
- Failure experiments.
- Runbooks and postmortem exercise.
- CI/CD and infrastructure documentation.

## Later evolution candidates

These are not commitments. Each requires an RFC and evidence.

- Automatic proxy bidding.
- Search and watchlists.
- Dynamic listing schemas.
- Feature-flag control plane and SDK.
- Transactional outbox and event streaming.
- Horizontal bidding partitions.
- Service extraction and data migration.
- Multi-tenant auction houses.
- Kubernetes and cloud infrastructure.
