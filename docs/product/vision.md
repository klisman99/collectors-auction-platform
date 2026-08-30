# Product vision

## Summary

Collectors Auction Platform is a web marketplace where people submit individual physical collectibles and sell them through real-time English ascending auctions.

The MVP is a production-minded learning laboratory, not a legally compliant commercial auction house. Email, payment, shipping, delivery, and identity verification are simulated so the project can concentrate on trustworthy product rules and backend engineering.

## Problem

Collectors need a trustworthy way to discover items, understand their declared condition, compete fairly, and receive an unambiguous auction result. Sellers need a moderated listing process and a transparent mechanism for reaching a market price across many kinds of collectibles.

The difficult part is not displaying a countdown. It is guaranteeing that concurrent bids, retries, late arrivals, suspensions, disqualifications, reconnects, process restarts, and closing decisions produce one durable and auditable outcome.

## Target users

### Collector

Browses scheduled, live, and ended auctions; watches live state; submits bids; and tracks purchases.

### Seller

Submits an owned collectible, describes its condition, schedules an approved item, and completes simulated settlement.

### Moderator

Uses a dedicated non-trading account to review item submissions and suspend auctions that require operational intervention.

### Administrator

Uses a dedicated non-trading account to manage operational accounts, suspend regular accounts, resolve suspended auctions, and inspect restricted audit information.

## Value proposition

- A flexible, category-neutral collectible listing model.
- Structured condition disclosure and policy-based moderation.
- Real-time bidding with server-authoritative ordering.
- Protection against last-second sniping.
- Optional hidden reserve price.
- Durable bid history, including visible administrative disqualifications.
- Explicit auction and simulated settlement outcomes.

## Product principles

1. Correctness before scale.
2. Server state is authoritative.
3. Accepted bids are immutable; administrative eligibility changes are append-only.
4. Every auction ends with one explicit outcome.
5. Published terms are visible, snapshotted, and stable.
6. Architectural complexity must be earned by evidence.
7. Exceptional operations are auditable and privacy-aware.
8. The UI never invents state that the backend has not confirmed.
9. A reconnect always recovers from an authoritative snapshot.

## MVP success criteria

The MVP is complete when a reproducible automated and manual demonstration proves that:

- a regular account verifies email, submits a generic collectible, and receives moderation;
- at least two bidders compete concurrently and retries do not duplicate bids;
- all clients converge on the same accepted-bid order;
- late bids extend the auction and suspension freezes the correct remaining time;
- account suspension permanently disqualifies applicable bids without deleting history;
- closing produces exactly one final outcome and at most one sale;
- restart does not lose accepted bids, deadlines, audit events, or the final result;
- the simulated settlement completes or fails according to its deadlines;
- 100 concurrent bidders achieve p95 bid responses below 500 ms and live events below one second without loss or duplication.

## Non-goals

- Operating a legally compliant commercial auction house.
- Real money movement, addresses, carriers, or identity verification.
- Formal authenticity certification.
- Lots, bundles, or fractional quantities.
- Search, recommendation, watchlists, reputation, or disputes.
- Automatic proxy bidding, bid retraction, or other auction formats.
- Multi-currency, internationalization, or native mobile applications.
- Starting with microservices, Kafka, Redis, or Kubernetes.
