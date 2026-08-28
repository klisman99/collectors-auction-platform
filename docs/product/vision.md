# Product vision

## Summary

Collectors Auction Platform is a web marketplace where people can submit modern collectibles and sell them through real-time English ascending auctions.

The MVP focuses on collectible trading cards. Payments, shipping, email delivery, and identity verification are simulated so the project can focus on product rules and backend engineering.

## Problem

Collectors need a trustworthy way to discover items, understand their condition, compete fairly, and receive an unambiguous auction result. Sellers need a structured listing process and a transparent mechanism for reaching a market price.

The technically difficult part is not displaying a countdown. It is guaranteeing that concurrent bids, late arrivals, retries, disconnections, and closing decisions produce one durable and auditable outcome.

## Target users

### Collector

Finds items, watches live auctions, submits bids, and tracks purchases.

### Seller

Submits an owned collectible, provides condition information, and schedules an approved item for auction.

### Moderator

Reviews listings, rejects misleading submissions, and suspends auctions when a rule is violated.

### Administrator

Manages accounts and platform-level policies. Administration is intentionally small in the MVP.

## Value proposition

- Structured collectible descriptions and moderation.
- Real-time bidding with server-authoritative ordering.
- Protection against last-second sniping.
- Optional hidden reserve price.
- Durable bid history and explicit auction outcomes.
- A clear simulated post-sale journey.

## Product principles

1. Correctness before scale.
2. Server state is authoritative.
3. Accepted bids are immutable.
4. Every auction ends with one explicit outcome.
5. Rules are visible and versioned.
6. Architectural complexity must be earned by evidence.
7. Exceptional operations are auditable.
8. The UI never invents state that the backend has not confirmed.

## MVP success criteria

The MVP is successful when it can repeatedly demonstrate the following scenario:

- one seller submits an item;
- one moderator approves it;
- at least two bidders compete concurrently;
- all connected clients converge on the same accepted-bid order;
- a bid inside the protection window extends the auction;
- retries do not create duplicate bids;
- closing creates exactly one final outcome;
- a restart does not lose accepted bids or the final result.

## Non-goals

- Operating a legally compliant commercial auction house.
- Real money movement.
- Real shipping integrations.
- Formal authenticity certification.
- Mobile applications.
- Supporting every collectible category.
- Starting with microservices, Kafka, Redis, or Kubernetes.
