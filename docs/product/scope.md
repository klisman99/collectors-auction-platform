# MVP scope

## Product locale

- User-facing interface, validation, and simulated email are in English.
- Money is represented in BRL.
- Authoritative instants are persisted in UTC and displayed in `America/Sao_Paulo`.

## Included

### Accounts and access

- Register a regular account with normalized email, immutable public handle, and password.
- Simulate email verification and password recovery through Mailpit.
- Persist, revoke, and expire server-side sessions.
- Allow a suspended account to sign in with restricted access to history and existing settlements.
- Invite dedicated moderator and administrator accounts.
- Deactivate privileged accounts without converting them into trading accounts.
- Apply basic single-instance rate limits to login, recovery, and bid attempts.

### Collectible items

- Create one physical collectible per listing; lots are excluded.
- Select one category: Cards, Coins and Currency, Stamps, Comics and Books, Toys and Figures, Memorabilia, Art and Antiques, or Other.
- Record title, category, description, condition, condition notes, and an ownership declaration.
- Upload, order, validate, normalize, and thumbnail one to five managed images.
- Submit an item for policy and consistency review.
- Approve or reject a submission with an auditable reason.
- Invalidate approval after a material edit and prevent edits during a non-terminal auction.
- Relist unchanged items after unsold, cancelled, or failed-settlement outcomes.

### Auctions and discovery

- Create an English ascending auction for an approved item.
- Configure opening amount, minimum increment, optional hidden reserve, start time, and end time within fixed policy limits.
- Snapshot the item and applicable policy values when the auction is scheduled.
- Reschedule before start and reduce, but never increase, a scheduled reserve.
- Start, extend, suspend, resume, close, or cancel according to business rules.
- Browse simple public lists of scheduled, live, and ended auctions without search.
- Expose the exact reserve only to the seller and administrators.

### Bidding and realtime

- Place manual idempotent bids through HTTP.
- Allow the current leader to raise its own bid.
- Reject ineligible, rate-limited, late, seller, or under-minimum attempts.
- Serialize competing commands through a deliberate PostgreSQL locking strategy.
- Assign a per-auction acceptance sequence to durable bids.
- Show public value, server time, and an auction-local bidder pseudonym.
- Permanently disqualify bids after account suspension without deleting them.
- Broadcast committed bids, deadlines, disqualifications, lifecycle changes, and outcomes over public read-only STOMP topics.
- Recover after reconnect by loading a fresh REST snapshot.

### Outcome and simulated settlement

- Sell automatically when there is an eligible bid and the reserve is absent or met.
- End without sale when there are no eligible bids.
- Ask the seller to accept or reject the highest below-reserve bid within 24 hours.
- Simulate payment, shipment with carrier and tracking reference, delivery confirmation, deadline failure, and automatic completion.
- Release the item after failed settlement and archive it after completion.
- Expose private purchase/sale history and layered audit timelines.

### Local operation

- Run locally through Docker Compose with PostgreSQL, MinIO, and Mailpit.
- Offer an optional observability profile with Prometheus, Grafana, and Tempo.
- Build and validate the system through GitHub Actions without requiring a public deployment.

## Excluded

- Payment providers, financial settlement, addresses, freight pricing, and carrier integrations.
- Government, legal, KYC, or commercial-auction compliance.
- Formal authenticity certification or appraisals.
- Lots, quantities, and category-specific schemas.
- Search, recommendations, watchlists, and outbid email.
- Automatic maximum bids, Dutch auctions, sealed bids, and bid retraction.
- Full dispute, refund, or arbitration workflows.
- Multi-currency, multi-language, multi-region, or multi-tenant operation.
- Native mobile applications and machine-learning fraud detection.

## Later product capabilities

- Category-specific attributes layered over the generic item model.
- Search, filters, watchlists, and outbid notifications.
- Reputation and seller history.
- Automatic maximum bids.
- Authenticity and settlement disputes.
- Auction-house organizations and multi-tenancy.
- Feature flags and experimentation.
