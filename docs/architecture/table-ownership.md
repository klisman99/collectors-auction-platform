# Database table ownership

PostgreSQL is the source of truth. Each table below is written only by its owner; cross-module
access happens through a deliberate public API or a durable event, never a direct table query.

| Table | Owner | Purpose | Access rule |
| --- | --- | --- | --- |
| `regular_accounts` | `identity` | Regular-account credentials and eligibility state | Other modules obtain authorization facts through an identity public contract. |
| `email_verification_tokens` | `identity` | Single-use email verification tokens | Identity only. |
| `password_recovery_tokens` | `identity` | Single-use password recovery tokens | Identity only. |
| `collectible_items` | `catalog` | Seller-owned collectible drafts and lifecycle | Other modules use catalog commands/snapshots. |
| `collectible_item_media` | `catalog` | Private, normalized media metadata and order | Catalog only; object storage is accessed through its storage port. |
| `collectible_item_reviews` | `moderation` | Durable moderation decision attached to a collectible lifecycle | Moderation only; it invokes catalog's deliberate lifecycle commands. |
| `auctions` | `auctions` | Published terms, lifecycle and suspension timing state, and immutable item/policy snapshots | Auctions only; other modules use deliberate auction contracts. |
| `auction_item_snapshot_media` | `auctions` | Ordered media metadata and binary content frozen when an auction is published | Auctions only. |
| `auction_timeline_events` | `auctions` | Ordered lifecycle events with public reasons and restricted administrative detail | Auctions only. |
| `bidder_pseudonyms` | `bidding` | Stable auction-local public names for bidders | Bidding only. |
| `accepted_bids` | `bidding` | Immutable sequenced bids accepted under the auction lock | Bidding only. |
| `bid_attempts` | `bidding` | Accepted, rejected, duplicate, conflicting, and rate-limited command outcomes | Bidding only. |
| `audit_records` | `audit` | Append-only auditable facts | Product modules publish facts; audit persists projections. |
| `spring_session` | `platform` | Server-side HTTP session metadata | Only Spring Session through platform configuration. |
| `spring_session_attributes` | `platform` | Server-side HTTP session attributes | Only Spring Session through platform configuration. |
| `event_publication` | `platform` | Spring Modulith durable event-publication registry | Only Spring Modulith infrastructure; originating modules publish events transactionally. |

Future migrations must add the table to this map in the same change. A migration may create
foreign keys to another module's aggregate only when the architecture document explicitly
allows that relationship; it does not permit direct repository access.
