# Use cases

## UC-01 — Register, verify, and authenticate

**Primary actor:** visitor

The visitor registers a regular account, chooses an immutable public handle, receives a simulated verification email, verifies, and signs in through a revocable session.

**Outcome:** a verified account may trade; an unverified account may authenticate and browse but cannot submit, schedule, or bid.

**Important failures:** duplicate email or handle, invalid credentials, expired/single-use token, rate limit, or expired session.

## UC-02 — Recover credentials and manage sessions

**Primary actor:** regular user

The user requests a simulated recovery email, resets the password, signs out, or revokes all sessions.

**Outcome:** password reset and account suspension revoke existing sessions; new authentication reflects current account restrictions.

## UC-03 — Manage operational accounts

**Primary actor:** administrator

The administrator invites a dedicated moderator or administrator and may later deactivate it.

**Outcome:** operational access is auditable, non-trading, and never converts to or from a regular account.

## UC-04 — Create a collectible draft

**Primary actor:** verified active regular user acting as seller

The seller creates one generic physical collectible, supplies required structured data, accepts the ownership declaration, and uploads one to five processed images.

**Outcome:** a private editable draft is ready for submission.

## UC-05 — Submit and moderate a collectible

**Primary actors:** seller and moderator

The seller submits the item. A moderator or administrator checks completeness, consistency, condition disclosure, ownership declaration, and prohibited-content policy, then approves or rejects.

**Outcome:** an approved unchanged item may be auctioned; rejection returns it to draft with a reason.

## UC-06 — Schedule and publish an auction

**Primary actor:** seller

The seller configures opening amount, increment, optional reserve, start, and end for an approved item.

**Outcome:** published item and policy snapshots are immutable, and the auction appears in public scheduled discovery.

## UC-07 — Reschedule, start, or seller-cancel

**Primary actors:** seller and auction system

Before start the seller may reschedule, lower the reserve, or cancel with a public reason. Server time starts due auctions and restart reconciliation catches missed transitions.

**Outcome:** the auction becomes live inside its authoritative interval or ends cancelled without losing its audit history.

## UC-08 — Browse and view a live auction

**Primary actor:** visitor or authenticated user

The client browses public scheduled, live, and ended lists, loads an authoritative auction snapshot, and may subscribe read-only to STOMP projections.

**Outcome:** the client converges on server state after reconnect without relying on message delivery for correctness.

## UC-09 — Place a bid

**Primary actor:** eligible bidder

The bidder submits an amount and idempotency key through HTTP. The system rate-limits, locks auction bidding state, validates, persists, sequences, and only then acknowledges.

**Outcome:** the command is accepted, rejected with a business reason, or returned as a previous result.

**Concurrency concern:** multiple commands may compete against the same current amount; one serialized durable order must result.

## UC-10 — Extend closing

**Primary actor:** auction system

An eligible bid accepted inclusively within the final two minutes moves the effective end to two minutes after acceptance.

**Outcome:** the persisted deadline changes and committed projections notify connected clients.

## UC-11 — Suspend or cancel an auction

**Primary actors:** moderator and administrator

A moderator or administrator suspends a scheduled or live auction with a public reason. An administrator later resumes/releases or cancels it and chooses item disposition.

**Outcome:** live time is frozen fairly, scheduled start is prevented, and every exceptional action is audited.

## UC-12 — Suspend a regular account

**Primary actor:** administrator

The administrator suspends a regular account with a public reason and optional internal note.

**Outcome:** sessions are revoked, seller auctions are suspended, all accepted bids in non-terminal auctions are permanently disqualified, and public state is recalculated without deleting history.

## UC-13 — Close an auction

**Primary actor:** auction system

After effective end, the system atomically prevents new bids and selects the highest eligible accepted bid.

**Outcome:** sold, unsold, or awaiting seller decision. Retry cannot create another result or sale.

## UC-14 — Decide a below-reserve offer

**Primary actor:** seller

The seller accepts or rejects the eligible final bid within 24 hours. Disqualification may replace the offer and restart the window.

**Outcome:** a sale is created or the auction ends unsold.

## UC-15 — Simulate settlement

**Primary actors:** buyer and seller

The buyer simulates payment, the seller supplies carrier and tracking reference, and the buyer confirms delivery. Durable deadlines may fail or automatically complete the settlement.

**Outcome:** completion archives the item; failure releases it unchanged for another auction.

## UC-16 — Inspect history and audit

**Primary actors:** visitor, regular user, moderator, and administrator

Each actor loads the timeline allowed by its role.

**Outcome:** public facts, participant history, operational records, and internal notes remain correctly separated.
