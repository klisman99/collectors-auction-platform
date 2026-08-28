# Domain invariants

Invariants are conditions that must remain true regardless of retries, concurrency, process restarts, or delivery order.

## Identity

- A suspended account cannot create an item, schedule an auction, or place a new bid.
- Historical actions remain associated with the account after suspension.
- A user cannot bid in an auction for an item owned by that user.

## Item and auction ownership

- One auction has exactly one seller and one collectible item snapshot.
- One collectible item cannot participate in two non-terminal auctions.
- Publishing an auction freezes the item information shown to bidders.

## Money

- Monetary values use integer BRL cents.
- Opening amount, reserve, minimum increment, and bids are positive.
- Accepted bid amounts strictly increase.
- The stored final amount equals the highest accepted bid selected at closing.

## Bidding

- An auction accepts bids only while live and before its effective end time.
- Every accepted bid belongs to exactly one auction and bidder.
- An accepted bid is never updated or deleted by normal application behavior.
- One idempotency key maps to at most one bid-command result for a bidder and auction.
- At most one of two competing bids for the same required next amount is accepted.
- An acknowledgement cannot precede durable persistence.
- Public bid order equals server acceptance order, not client clock order.

## Closing

- Closing changes the auction out of the bid-accepting state exactly once.
- A terminal auction never accepts another bid.
- One auction has at most one final winner.
- One auction creates at most one sale.
- A sold auction with accepted bids uses its highest accepted bidder as winner.
- Closing and seller-decision handlers are idempotent.

## Real-time projection

- WebSocket events do not replace the database as source of truth.
- A reconnecting client can load a snapshot and continue from authoritative state.
- Missing or duplicated real-time messages cannot corrupt auction state.

## Audit

- Sensitive administrative and terminal business actions produce append-only audit events.
- An audit event uses server time and identifies its actor or system origin.
