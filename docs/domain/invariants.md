# Domain invariants

Invariants remain true regardless of retries, concurrency, process restarts, listener delivery, or client message order.

## Identity and authorization

- Email and public handle are each unique without regard to case.
- A public handle never changes after registration.
- An unverified regular account never submits an item, schedules an auction, or places a bid.
- A suspended regular account creates no new marketplace activity but may read history and finish existing settlements.
- Operational accounts never own items, auctions, bids, or sales as participants.
- The system always retains at least one active administrator.
- An operational activation invitation is single-use and expires after 24 hours.
- Deactivated operational accounts never regain access through authentication or invitation.
- Historical actions remain associated with an account after suspension or deactivation.

## Item and ownership

- One item represents one physical object and has exactly one regular-account owner.
- An item has one to five safe managed images before submission.
- One auction has exactly one seller and one immutable collectible snapshot.
- One collectible item participates in at most one non-terminal auction.
- A non-terminal auction prevents item edits.
- A material edit never preserves a previous approval.
- A completed settlement permanently archives its item.

## Money and timing

- Monetary values use integer BRL cents between the published minimum and maximum.
- Reserve, when present, is not lower than opening.
- Authoritative instants are UTC and server time determines transitions.
- A bid is timely exactly when `start <= acceptedAt < effectiveEnd`.
- The effective end never moves backwards.
- Published auction policy values do not change after scheduling.

## Bidding and eligibility

- Every accepted bid belongs to exactly one auction and regular-account bidder.
- Accepted bids are never updated or deleted by application behavior.
- Each accepted bid has a unique, monotonically increasing sequence within its auction.
- Eligible accepted amounts strictly increase in acceptance order until an administrative disqualification recalculates eligibility.
- A disqualification is append-only, permanent, and never changes the original accepted-bid record.
- One idempotency key maps to at most one command result for a bidder and auction.
- At most one competing command for the same required next amount is accepted.
- An acknowledgement or live projection never precedes durable persistence.
- Public bid order is server acceptance order, not client time.
- Public bid identity is auction-local; persistent bidder identity remains hidden until sale creation.

## Suspension and closing

- A suspended auction accepts no bids and performs no automatic closing transition until resolved.
- Live suspension preserves remaining duration and resume restores exactly that duration.
- Scheduled suspension never starts automatically and returns to draft only through administrator release.
- A terminal auction never accepts another bid or returns to a bidding state.
- Closing changes the auction out of bid-accepting state exactly once.
- Closing selects only eligible accepted bids.
- One auction has at most one winner, one final outcome, and one sale.
- Sold-auction participants never change because of later account suspension.
- Closing and seller-decision handlers are idempotent.

## Settlement

- One sold auction creates exactly one sale.
- Only the buyer advances payment or delivery confirmation; only the seller records shipment.
- A sale reaches Completed or Failed at most once and never leaves a terminal settlement state.
- Failed settlement releases the unchanged item; completed settlement archives it.
- Suspended participants may act only on settlements that existed before suspension.

## Realtime, internal events, and recovery

- PostgreSQL is the source of truth; STOMP events never decide bid acceptance or outcomes.
- A reconnecting client can load a complete authoritative snapshot.
- Missing, duplicated, or reordered realtime messages cannot corrupt durable state.
- Scheduled work is discoverable from durable deadlines after restart.
- Internal event publication is durable, so listener failure cannot silently discard required audit or notification work.

## Audit and privacy

- Sensitive administrative and terminal actions produce append-only audit events.
- Every audit event uses server time and identifies an actor or system origin.
- Internal notes never appear in public or participant projections.
- Exact reserve and persistent bidder identity are exposed only to explicitly authorized actors.
