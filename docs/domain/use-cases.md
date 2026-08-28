# Use cases

## UC-01 — Register and authenticate

**Primary actor:** visitor

**Outcome:** an account and revocable authenticated session are created.

**Important failures:** duplicate email, invalid credentials, suspended account, expired session.

## UC-02 — Submit a collectible

**Primary actor:** active user acting as seller

The user creates a trading-card draft, supplies the required attributes and images, and submits it for moderation.

**Outcome:** the item is waiting for review and cannot be silently edited.

## UC-03 — Moderate a collectible

**Primary actor:** moderator

The moderator reviews the description and images, then approves or rejects the item with a reason.

**Outcome:** an approved item may be scheduled for auction; a rejected item returns to an editable state.

## UC-04 — Schedule an auction

**Primary actor:** item owner

The seller configures opening amount, increment, reserve, start time, and end time for an approved item.

**Outcome:** the auction is scheduled and uses an immutable item snapshot.

## UC-05 — View a live auction

**Primary actor:** visitor or authenticated user

The client loads the current auction snapshot and may subscribe to real-time accepted bids and deadline changes.

**Outcome:** the client converges to server-authoritative state even after reconnecting.

## UC-06 — Place a bid

**Primary actor:** eligible authenticated bidder

The bidder submits an amount and idempotency key.

**Outcome:** the command is accepted, rejected with a business reason, or returned as a previously processed retry.

**Concurrency concern:** multiple commands may compete against the same current amount.

## UC-07 — Extend closing

**Primary actor:** auction system

An accepted bid inside the protection window moves the effective end time.

**Outcome:** the new deadline is persisted and broadcast.

## UC-08 — Close an auction

**Primary actor:** auction system

After the effective end time, the system prevents further bids and determines one result.

**Outcome:** sold, unsold, or awaiting seller decision. Retrying closing does not create another result or sale.

## UC-09 — Decide a below-reserve offer

**Primary actor:** seller

The seller accepts or rejects the final highest bid before the decision deadline.

**Outcome:** a sale is created or the auction ends unsold.

## UC-10 — Simulate settlement

**Primary actors:** buyer and seller

The parties confirm simulated payment, shipment, and delivery.

**Outcome:** the sale is completed with an auditable transition history.

## UC-11 — Suspend an account or auction

**Primary actor:** moderator or administrator

A privileged user suspends an account or live auction with a reason.

**Outcome:** prohibited new actions stop immediately while historical state remains intact.
