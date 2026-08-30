# Domain glossary

## Regular account

A person's trading identity, credentials, immutable public handle, verification state, and sessions. It may be active or suspended.

## Operational account

A dedicated invited moderator or administrator identity. Operational accounts never sell or bid and may be active or deactivated.

## User

An authenticated person represented by an account. A suspended regular user has restricted access to history and existing settlements.

## Seller

The active regular account that owns the collectible item offered by a specific auction. Seller is contextual, not a global role.

## Bidder

An eligible verified regular account that attempts to place a bid in a specific auction. Bidder is contextual, not a global role.

## Moderator

A privileged operational user who reviews item submissions and may suspend auctions.

## Administrator

A privileged operational user who manages operational and regular-account status, resolves suspended auctions, and accesses restricted audit information.

## Collectible item

One seller-owned physical object described through the MVP's generic listing schema. A collectible item is not a lot or quantity.

## Category

One controlled classification: Cards, Coins and Currency, Stamps, Comics and Books, Toys and Figures, Memorabilia, Art and Antiques, or Other.

## Condition

The seller's structured declaration of physical condition: New/Sealed, Excellent, Very Good, Good, Fair, Poor, or Not Applicable. It is accompanied by required condition notes.

## Item snapshot

An immutable copy of the published item description and media references attached to a scheduled auction. Later catalog edits cannot change what bidders saw.

## Auction

A timed English ascending sale process for one approved collectible item.

## Opening amount

The minimum amount of the first acceptable bid.

## Minimum increment

The smallest allowed increase over the current eligible accepted amount.

## Reserve price

An optional hidden threshold below which the seller is not automatically obligated to sell. Absence of a reserve means any eligible bid can sell the item.

## Current amount

The amount of the highest eligible accepted bid, or the opening amount before the first eligible bid.

## Bid command

A bidder's HTTP request to offer an amount. A command may be accepted, rejected, rate-limited, or deduplicated.

## Bid attempt

Any bid command received by the application, including rejected and duplicate attempts.

## Accepted bid

A durable, immutable bid with a server-assigned per-auction sequence. Its eligibility may later change without editing or deleting it.

## Bid disqualification

An append-only administrative consequence of account suspension that permanently makes an accepted bid ineligible for leadership or outcome selection.

## Bidder pseudonym

A stable identifier scoped to one account and auction that is shown in public bid history instead of the persistent account handle.

## Idempotency key

A client-generated identifier scoped to bidder and auction that permits safe command retry without creating a second accepted bid.

## Protection window

The final two minutes before the current effective end time, during which an accepted bid moves the deadline two minutes forward from acceptance.

## Effective end time

The authoritative closing deadline after accepted extensions and any resumed live-suspension duration.

## Auction suspension

A non-terminal operational state that prevents bidding and automatic start or close until an administrator resumes, releases, or cancels the auction.

## Auction outcome

The single terminal result: sold, unsold, or cancelled. Awaiting seller decision and suspension are non-terminal states.

## Sale

The post-auction relationship created between seller and winner after a sold outcome.

## Settlement

The simulated payment, shipment, delivery confirmation, completion, or failure lifecycle owned by a sale.

## Audit event

An append-only record of a sensitive business or administrative fact with layered public, participant, moderator, and administrator visibility.
