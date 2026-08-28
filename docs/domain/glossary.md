# Domain glossary

## Account

A person's identity and credentials in the platform. An account may be active or suspended.

## User

An authenticated person represented by an account. A user may act as a seller in one auction and bidder in another.

## Seller

The owner of the collectible item offered by a specific auction. Seller is contextual, not a global account role.

## Bidder

An eligible user who attempts to place a bid in a specific auction. Bidder is contextual, not a global account role.

## Moderator

A privileged user who reviews item submissions and handles exceptional auction operations.

## Collectible item

The seller-owned asset described in the catalog. The MVP supports trading cards.

## Item snapshot

An immutable copy of the relevant item description attached to a published auction. Later catalog edits cannot silently change what bidders saw.

## Auction

A timed English ascending sale process for one approved collectible item.

## Opening amount

The minimum amount of the first acceptable bid.

## Minimum increment

The smallest allowed increase over the current accepted amount.

## Reserve price

An optional hidden threshold below which the seller is not automatically obligated to sell.

## Current amount

The amount of the latest highest accepted bid, or the opening amount before the first bid.

## Bid command

A bidder's request to offer an amount. A command may be accepted, rejected, or deduplicated.

## Accepted bid

A durable and immutable bid that changed the auction's highest bid.

## Bid attempt

Any bid command received by the platform, including rejected and duplicate attempts.

## Idempotency key

A client-generated identifier that allows a bid command to be safely retried without creating a second accepted bid.

## Protection window

The final period of an auction during which an accepted bid extends the effective end time.

## Effective end time

The current authoritative closing deadline, including all accepted extensions.

## Auction outcome

The single final auction result: sold, unsold, or cancelled. Suspension is operational and not a successful final outcome.

## Sale

The post-auction relationship created between seller and winner after a sold outcome.

## Settlement

The simulated payment, shipment, delivery, and completion process for a sale.

## Audit event

An append-only record of a sensitive business or administrative action.
