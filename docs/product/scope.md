# MVP scope

## Included

### Accounts

- Register with email and password.
- Sign in and sign out.
- Persist and revoke sessions.
- Simulate email verification and password recovery.
- Suspend an account.
- Distinguish regular users, moderators, and administrators.

Seller and bidder are contextual relationships, not global roles. The same user may sell in one auction and bid in another.

### Collectible cards

- Create and edit a draft item.
- Upload or reference item images.
- Record game, set, card name, number, language, rarity, condition, grading status, grader, grade, and certificate number.
- Submit an item for moderation.
- Approve or reject the submission with a reason.
- Prevent the same item from participating in multiple active auctions.

### Auctions

- Create an English ascending auction for an approved item.
- Configure opening price, minimum increment, optional hidden reserve, start time, and end time.
- Schedule, start, extend, close, cancel, or suspend an auction according to the business rules.
- Show whether the reserve has been met without exposing its value.

### Bidding

- Place manual bids.
- Reject bids from ineligible accounts or the seller.
- Enforce minimum increments.
- Deduplicate requests using an idempotency key.
- Order accepted bids using server-side authority.
- Broadcast accepted bids and closing-time changes.
- Protect closing with a two-minute extension window.

### Outcome and simulated settlement

- Sell automatically when the reserve is met.
- End without sale when there are no bids.
- Ask the seller to accept or reject the highest bid when reserve is not met.
- Simulate payment, shipment, delivery, and completion.
- Record audit events for sensitive operations.

## Excluded

- Payment providers and actual financial settlement.
- Carrier integrations and real shipment tracking.
- Government or legal integrations.
- Automatic proxy bidding.
- Dutch or sealed-bid auctions.
- Bid retraction.
- Multi-currency support.
- Native mobile applications.
- Machine-learning fraud detection.
- Full dispute and arbitration workflows.
- Multiple collectible schemas.
- Multi-region deployment.

## Later product capabilities

- Automatic maximum bids.
- Watchlists and outbid notifications.
- Reputation and seller history.
- Search and recommendations.
- Dynamic category-specific listing forms.
- Authenticity disputes.
- Auction-house organizations and multi-tenancy.
- Feature flags and experimentation.
