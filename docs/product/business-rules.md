# Business rules

These rules define the initial product behavior. Rule identifiers must be referenced by implementation issues, tests, API errors, and future design documents.

## Identity and access

- **BR-AUTH-001:** Regular-account email is trimmed, normalized for case-insensitive uniqueness, and never exposed publicly.
- **BR-AUTH-002:** A regular account has an immutable public handle of 3 to 30 ASCII letters, digits, or underscores, unique without regard to case.
- **BR-AUTH-003:** A password contains 12 to 128 characters and has no composition requirement.
- **BR-AUTH-004:** Email verification is required before a regular account may submit an item, schedule an auction, or place a bid.
- **BR-AUTH-005:** A verification token is single-use and expires after 24 hours; a password-recovery token is single-use and expires after one hour.
- **BR-AUTH-006:** Password reset revokes every existing session for the account.
- **BR-AUTH-007:** A session expires after 30 days without activity or 90 days after creation, whichever occurs first.
- **BR-AUTH-008:** Sign-out revokes the current session, and an account may revoke all of its sessions.
- **BR-AUTH-009:** A suspended regular account may authenticate in restricted mode, view history, and complete existing settlements, but cannot submit or edit items, schedule auctions, place bids, or change profile data.
- **BR-AUTH-010:** Suspending a regular account revokes its existing sessions so subsequent access receives current restrictions.
- **BR-AUTH-011:** Moderator and administrator accounts are dedicated operational accounts created by invitation and cannot sell or bid.
- **BR-AUTH-012:** Deactivating an operational account revokes access permanently without converting it into a regular account; historical actions remain attributable.
- **BR-AUTH-013:** Moderators and administrators may moderate items and suspend auctions. Only administrators may manage operational accounts, suspend or reactivate regular accounts, resolve suspended auctions, and view exact reserve prices operationally.
- **BR-AUTH-014:** The last active administrator cannot deactivate itself or otherwise leave the platform without an active administrator.
- **BR-AUTH-015:** Login is limited to five attempts per minute per IP-and-email pair; password recovery is limited to three requests per hour; bidding is limited to ten commands per second per account and auction. Exceeded limits return HTTP 429 without changing domain state.

## Collectible items

- **BR-ITEM-001:** One item record represents exactly one physical collectible; lots, bundles, quantities, and fractional ownership are not accepted.
- **BR-ITEM-002:** Category is one of Cards, Coins and Currency, Stamps, Comics and Books, Toys and Figures, Memorabilia, Art and Antiques, or Other.
- **BR-ITEM-003:** Category Other requires a short seller-provided category label.
- **BR-ITEM-004:** Submission requires a title of 5 to 120 characters, description of 20 to 5000 characters, condition, condition notes of 10 to 2000 characters, and an affirmative ownership declaration.
- **BR-ITEM-005:** Condition is New/Sealed, Excellent, Very Good, Good, Fair, Poor, or Not Applicable.
- **BR-ITEM-006:** Submission requires one to five ordered JPEG, PNG, or WebP images, each no larger than 5 MB.
- **BR-ITEM-007:** Uploaded image content is validated from its bytes, safely re-encoded, stripped of metadata, and given a display rendition and thumbnail before it can be published.
- **BR-ITEM-008:** Draft media is private. Published media is reachable through stable platform-controlled URLs while object storage remains private.
- **BR-ITEM-009:** A draft may be edited or deleted only by its owner.
- **BR-ITEM-010:** A submitted item is read-only until approved or rejected.
- **BR-ITEM-011:** Rejection requires a reason and returns the item to an editable draft.
- **BR-ITEM-012:** Editing title, category, description, condition, condition notes, ownership declaration, or images invalidates an existing approval and returns the item to draft.
- **BR-ITEM-013:** An item attached to a non-terminal auction cannot be edited or deleted.
- **BR-ITEM-014:** An approved item can be scheduled only by its owner.
- **BR-ITEM-015:** One item cannot belong to more than one non-terminal auction.
- **BR-ITEM-016:** A scheduled auction stores an immutable item snapshot of everything shown to bidders.
- **BR-ITEM-017:** An unchanged item is eligible for another auction after an unsold outcome, cancellation, or failed settlement.
- **BR-ITEM-018:** A completed settlement archives the item and prevents relisting.

## Moderation

- **BR-MOD-001:** Moderation verifies required data, correspondence between text and images, declared condition, ownership declaration, and prohibited-content policy; it does not certify authenticity or value.
- **BR-MOD-002:** Illegal, stolen, counterfeit, dangerous, weapon, drug, sexually explicit, hateful, or living items are prohibited.
- **BR-MOD-003:** Only active moderators and administrators may approve or reject submissions.
- **BR-MOD-004:** A moderator or administrator cannot change a decision that has already been persisted for the same review; concurrent later decisions fail with a conflict.
- **BR-MOD-005:** Approval and rejection record actor, server time, public reason where applicable, and optional internal note.
- **BR-MOD-006:** Administrative auction cancellation may either release an unchanged approved item or revoke approval and return it to draft.

## Auction configuration and discovery

- **BR-AUC-001:** The MVP supports English ascending auctions in BRL only.
- **BR-AUC-002:** Opening amount, minimum increment, reserve, and bids use positive integer BRL cents between R$ 10 and R$ 1,000,000 inclusive.
- **BR-AUC-003:** Reserve is optional and, when present, cannot be below the opening amount.
- **BR-AUC-004:** Bidders, visitors, and moderators see only whether the reserve is met. The exact reserve is visible to the seller and administrators.
- **BR-AUC-005:** Start must be at least five minutes after scheduling; duration must be between ten minutes and seven days.
- **BR-AUC-006:** Authoritative instants are UTC. User-facing times are displayed in `America/Sao_Paulo`.
- **BR-AUC-007:** Scheduling snapshots the item, protection-window duration, monetary limits, and other auction policy values that affect published terms.
- **BR-AUC-008:** Once scheduled, item snapshot, opening amount, and minimum increment cannot change.
- **BR-AUC-009:** Before start, the seller may reschedule within policy limits and may reduce, but never increase, the reserve.
- **BR-AUC-010:** A seller may cancel a scheduled auction before start only with a public reason and audit event.
- **BR-AUC-011:** A draft auction may be discarded without a cancellation record.
- **BR-AUC-012:** Simple public lists expose scheduled auctions ordered by start time, live auctions ordered by effective end time, and ended auctions ordered newest first.
- **BR-AUC-013:** Public auction details expose the immutable item snapshot, seller handle, opening/current amount, increment, reserve-met indicator, authoritative deadlines, state, eligible bid history, disqualifications, and public timeline.
- **BR-AUC-014:** Process restart reconciles durable scheduled and live auctions against server time: a scheduled auction becomes live if its interval remains open, and a due auction closes idempotently.

## Bid acceptance and privacy

- **BR-BID-001:** A bid is considered only while the auction is live and `start <= acceptedAt < effectiveEnd`; a bid at the exact effective end is late.
- **BR-BID-002:** Server time and transaction acceptance order are authoritative; client timestamps never determine precedence.
- **BR-BID-003:** The first valid bid may equal the opening amount. Every later bid must be at least the current eligible amount plus the minimum increment.
- **BR-BID-004:** A bidder may raise its own leading amount and may bid any amount at or above the required minimum.
- **BR-BID-005:** The seller and every operational account are ineligible to bid.
- **BR-BID-006:** Every bid command requires an idempotency key scoped to bidder and auction.
- **BR-BID-007:** Repeating the same key and payload returns the original result; reusing the key with a different payload is rejected.
- **BR-BID-008:** A bid command acquires a pessimistic PostgreSQL lock on the auction bidding state before validating and persisting its result.
- **BR-BID-009:** Every accepted bid receives a monotonically increasing sequence unique within its auction.
- **BR-BID-010:** If competing commands target the same required next amount, at most one can be accepted.
- **BR-BID-011:** The system persists an accepted bid before acknowledging or broadcasting it.
- **BR-BID-012:** An accepted bid is immutable and cannot be retracted.
- **BR-BID-013:** Public bid history shows amount, server acceptance time, sequence, and a stable auction-local bidder pseudonym; it never reveals email or persistent bidder handle.
- **BR-BID-014:** The seller sees the winner's persistent public handle only after a sale is created.
- **BR-BID-015:** Rejected and duplicate attempts are retained for operational analysis but do not appear in public bid history.
- **BR-BID-016:** Suspending a regular account permanently disqualifies all of its accepted bids in non-terminal auctions without deleting or editing those bids.
- **BR-BID-017:** A disqualified bid remains publicly visible and marked; account reactivation does not restore it.
- **BR-BID-018:** Disqualification recalculates eligible leader, current amount, minimum next amount, and reserve-met indicator, and publishes the change.
- **BR-BID-019:** A deadline extension caused by a later-disqualified bid remains effective; administrative action never shortens the effective end time.
- **BR-BID-020:** Sold auctions and existing sales are never recalculated after later account suspension.

## Closing protection, suspension, and cancellation

- **BR-CLOSE-001:** The protection window is two minutes and begins inclusively at `effectiveEnd - 2 minutes`.
- **BR-CLOSE-002:** A bid accepted in the protection window sets `effectiveEnd = acceptedAt + 2 minutes`.
- **BR-CLOSE-003:** Further late bids may extend the auction again; extension is a deadline change, not a separate auction state.
- **BR-CLOSE-004:** The frontend countdown is a projection and cannot close an auction.
- **BR-CLOSE-005:** Moderators and administrators may suspend a scheduled or live auction with a required public reason and optional internal note.
- **BR-CLOSE-006:** A live suspension stores its remaining duration. Only an administrator may resume it, setting `effectiveEnd = resumeAt + remainingDuration`, or cancel it.
- **BR-CLOSE-007:** A scheduled suspension prevents automatic start. An administrator may release it to draft for seller rescheduling or cancel it.
- **BR-CLOSE-008:** Suspending a seller automatically suspends every scheduled or live auction owned by that seller.
- **BR-CLOSE-009:** Administrative cancellation requires a public reason, optional internal note, audit event, and item disposition.
- **BR-CLOSE-010:** Closing atomically stops new bids, evaluates only eligible accepted bids, and records the final highest eligible bid at most once.

## Auction outcome

- **BR-OUT-001:** An auction with no eligible accepted bids ends unsold.
- **BR-OUT-002:** Without a reserve, any eligible accepted bid produces a sold outcome.
- **BR-OUT-003:** With a met reserve, the highest eligible bidder wins at its accepted amount.
- **BR-OUT-004:** A highest eligible bid below reserve moves the auction to awaiting seller decision.
- **BR-OUT-005:** The seller has 24 hours to accept or reject the below-reserve amount and the bidder remains committed during that period.
- **BR-OUT-006:** A suspended seller cannot decide; the original deadline continues and expires unsold.
- **BR-OUT-007:** If the current bidder is disqualified while awaiting decision, the system selects the next eligible bid. A new highest bid receives a fresh 24-hour decision window; absence of one ends unsold.
- **BR-OUT-008:** Seller acceptance creates a sale; rejection or deadline expiry ends unsold.
- **BR-OUT-009:** Closing and seller-decision commands are idempotent and create at most one final outcome and one sale.
- **BR-OUT-010:** A terminal auction never returns to a bidding state.

## Simulated settlement

- **BR-SALE-001:** A sold auction creates exactly one sale whose buyer is the winner and seller is the auction owner.
- **BR-SALE-002:** Settlement states are Payment Pending, Shipment Pending, Shipped, Completed, and Failed.
- **BR-SALE-003:** The buyer simulates payment within 24 hours; success moves to Shipment Pending and expiry moves to Failed.
- **BR-SALE-004:** The seller supplies simulated carrier and tracking reference within three days of payment; success moves to Shipped and expiry moves to Failed.
- **BR-SALE-005:** The buyer may confirm delivery after shipment, completing the settlement.
- **BR-SALE-006:** If the buyer does not confirm within seven days of shipment, the system completes the settlement automatically because disputes are out of scope.
- **BR-SALE-007:** Settlement actions and deadlines are based on server time and are idempotent.
- **BR-SALE-008:** A suspended buyer or seller may perform only the actions required to finish an already-created settlement.
- **BR-SALE-009:** Account suspension after sale creation does not change the auction winner or sale participants.
- **BR-SALE-010:** Failed settlement releases the unchanged approved item for relisting; completed settlement archives it.

## Notifications and realtime

- **BR-NOTIFY-001:** Simulated transactional email covers verification, recovery, moderation decision, auction start/cancellation, winning, below-reserve decision, and settlement deadlines.
- **BR-NOTIFY-002:** Outbid email is excluded; connected clients receive committed changes over realtime projections.
- **BR-NOTIFY-003:** Visitors may subscribe read-only to public auction STOMP topics. No bid command is accepted over STOMP.
- **BR-NOTIFY-004:** Reconnect loads a fresh REST snapshot before consuming subsequent events; missing or duplicate realtime messages cannot corrupt state.
- **BR-NOTIFY-005:** Notification failure cannot roll back an accepted bid or outcome. Durable internal event publication retries failed notification and audit listeners.

## Audit

- **BR-AUDIT-001:** Account and operational-account changes, moderation, scheduling, cancellation, suspension, resume, disqualification, closing, seller decision, and settlement transitions are audited.
- **BR-AUDIT-002:** Audit records are append-only from the application perspective and include actor or system origin, action, target, server time, and relevant metadata.
- **BR-AUDIT-003:** Public timelines contain safe auction events and public reasons but never internal notes or account identity behind bidder pseudonyms.
- **BR-AUDIT-004:** Regular accounts may see their own account and settlement history; moderators see records required for their operational scope; administrators see the complete history and internal notes.
- **BR-AUDIT-005:** Administrative actions accept a categorized public reason and an optional internal note. Sensitive internal notes never appear in public or participant views.
