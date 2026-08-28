# Business rules

These rules define the initial product behavior. Rule identifiers should be referenced by issues, tests, API errors, and future design documents.

## Identity and access

- **BR-AUTH-001:** Only an active account may submit an item or place a bid.
- **BR-AUTH-002:** Suspended accounts keep their historical bids, but cannot create new ones.
- **BR-AUTH-003:** A user cannot bid in an auction where that user is the seller.
- **BR-AUTH-004:** Only moderators and administrators may approve or reject an item.
- **BR-AUTH-005:** Only moderators and administrators may suspend a live auction.
- **BR-AUTH-006:** Sessions can be individually revoked and all sessions for an account can be revoked together.

## Collectible items

- **BR-ITEM-001:** The MVP accepts collectible trading cards only.
- **BR-ITEM-002:** A draft may be edited by its owner.
- **BR-ITEM-003:** A submitted item becomes read-only until approved or rejected.
- **BR-ITEM-004:** Rejection requires a reason and returns the item to an editable state.
- **BR-ITEM-005:** An approved item can be scheduled only by its owner.
- **BR-ITEM-006:** One item cannot belong to more than one auction in a non-terminal state.
- **BR-ITEM-007:** Material changes to an approved item require a new moderation review.
- **BR-ITEM-008:** Published auction details must retain an immutable snapshot of the item as it was offered.

## Auction configuration

- **BR-AUC-001:** The MVP supports English ascending auctions.
- **BR-AUC-002:** Monetary values are positive integer amounts in BRL cents.
- **BR-AUC-003:** An auction has an opening amount and a positive minimum increment.
- **BR-AUC-004:** A reserve price is optional and hidden from bidders.
- **BR-AUC-005:** Bidders may see only whether the reserve has been met.
- **BR-AUC-006:** Once the auction is scheduled, the seller cannot increase the reserve.
- **BR-AUC-007:** After the first accepted bid, the seller cannot change the opening amount, increment, reserve, start time, or normal end time.
- **BR-AUC-008:** A scheduled auction can be cancelled by the seller only before it becomes live.
- **BR-AUC-009:** Administrative cancellation or suspension requires a reason and an audit event.

## Bid acceptance

- **BR-BID-001:** A bid is considered only while the auction is live.
- **BR-BID-002:** Server time is authoritative.
- **BR-BID-003:** A bid must be at least the current accepted amount plus the minimum increment.
- **BR-BID-004:** The first valid bid may equal the opening amount.
- **BR-BID-005:** An accepted bid is immutable and cannot be retracted in the MVP.
- **BR-BID-006:** Every bid command requires an idempotency key scoped to the bidder and auction.
- **BR-BID-007:** Repeating a command with the same key and payload returns the original result.
- **BR-BID-008:** Reusing a key with a different payload is rejected.
- **BR-BID-009:** Client timestamps do not determine bid precedence.
- **BR-BID-010:** If two bids compete for the same amount, at most one can be accepted.
- **BR-BID-011:** The system persists an accepted bid before acknowledging or broadcasting it.
- **BR-BID-012:** Rejected attempts are recorded for operational analysis without appearing in the public bid history.

## Closing protection

- **BR-CLOSE-001:** The protection window is two minutes.
- **BR-CLOSE-002:** A bid accepted during the protection window moves the effective end time two minutes forward from the bid acceptance time.
- **BR-CLOSE-003:** Further late bids may extend the auction again.
- **BR-CLOSE-004:** Extension changes the effective end time; it is not a separate auction state.
- **BR-CLOSE-005:** The frontend countdown is a projection and cannot close an auction.
- **BR-CLOSE-006:** Closing must atomically prevent new bids and select the final highest accepted bid.

## Auction outcome

- **BR-OUT-001:** An auction with no accepted bids ends as unsold.
- **BR-OUT-002:** When the highest bid meets the reserve, the auction is sold to that bidder.
- **BR-OUT-003:** When the highest bid is below reserve, the auction waits for the seller decision.
- **BR-OUT-004:** The seller has 24 hours to accept the highest below-reserve bid.
- **BR-OUT-005:** Seller acceptance creates a sale at the accepted highest amount.
- **BR-OUT-006:** Seller rejection or decision expiry ends the auction as unsold.
- **BR-OUT-007:** Closing is idempotent and can create at most one sale.
- **BR-OUT-008:** A terminal auction cannot return to a bidding state.

## Simulated settlement

- **BR-SALE-001:** A sold auction creates exactly one sale.
- **BR-SALE-002:** The winner is the buyer and the auction owner is the seller.
- **BR-SALE-003:** Payment confirmation is simulated and auditable.
- **BR-SALE-004:** Shipment and delivery confirmations are simulated.
- **BR-SALE-005:** A sale can complete only after simulated payment and delivery.

## Audit

- **BR-AUDIT-001:** Account suspension, moderation decisions, auction suspension, cancellation, closing, seller decision, and settlement transitions are audited.
- **BR-AUDIT-002:** Audit records are append-only from the application perspective.
- **BR-AUDIT-003:** Audit records include actor, action, target, server timestamp, and relevant metadata.
