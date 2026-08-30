# State machines

Regular accounts, operational accounts, items, auctions, bid eligibility, and sales have independent lifecycles. Combining them in one status field would make valid transitions ambiguous.

## Regular account

~~~mermaid
stateDiagram-v2
    [*] --> PendingVerification: register
    PendingVerification --> Active: verify email
    Active --> Suspended: administrator suspends
    Suspended --> Active: administrator reactivates
~~~

A suspended account may authenticate in restricted mode. Verification is a separate prerequisite for trading, not an operational role.

## Operational account

~~~mermaid
stateDiagram-v2
    [*] --> Invited: administrator invites
    Invited --> Active: accept invitation
    Active --> Deactivated: administrator deactivates
    Invited --> Deactivated: revoke invitation
    Deactivated --> [*]
~~~

Operational accounts never convert to regular accounts.

## Collectible item

~~~mermaid
stateDiagram-v2
    [*] --> Draft
    Draft --> UnderReview: submit
    UnderReview --> Approved: approve
    UnderReview --> Draft: reject with reason
    Approved --> Draft: material edit
    Approved --> Archived: settlement completed
    Archived --> [*]
~~~

Unsold, cancelled, and failed-settlement paths leave an unchanged item Approved. A non-terminal auction locks item editing independently of item status.

## Auction

~~~mermaid
stateDiagram-v2
    [*] --> Draft
    Draft --> Scheduled: schedule
    Scheduled --> Live: start reached
    Scheduled --> Scheduled: seller reschedules or lowers reserve
    Scheduled --> Cancelled: seller cancels with reason
    Scheduled --> Suspended: operational suspension
    Live --> Suspended: operational suspension
    Suspended --> Draft: admin releases scheduled suspension
    Suspended --> Live: admin resumes with remaining duration
    Suspended --> Cancelled: admin cancels
    Live --> Closing: effective end reached
    Closing --> Sold: no reserve or reserve met
    Closing --> Unsold: no eligible bids
    Closing --> AwaitingSellerDecision: eligible high bid below reserve
    AwaitingSellerDecision --> AwaitingSellerDecision: bidder disqualified and next offer selected
    AwaitingSellerDecision --> Sold: seller accepts
    AwaitingSellerDecision --> Unsold: seller rejects, deadline expires, or no eligible bid remains
    Sold --> [*]
    Unsold --> [*]
    Cancelled --> [*]
~~~

`Suspended` retains the source state. A scheduled suspension can only be released to Draft for explicit rescheduling; a live suspension can only resume with the stored remaining duration. Extension updates the deadline while state remains Live.

## Accepted-bid eligibility

~~~mermaid
stateDiagram-v2
    [*] --> Eligible: bid accepted
    Eligible --> Disqualified: bidder account suspended
    Disqualified --> [*]
~~~

The accepted bid itself never changes. Disqualification is a separate permanent append-only fact and is not applied after the auction is sold.

## Sale

~~~mermaid
stateDiagram-v2
    [*] --> PaymentPending
    PaymentPending --> ShipmentPending: buyer simulates payment
    PaymentPending --> Failed: payment deadline expires
    ShipmentPending --> Shipped: seller records carrier and tracking
    ShipmentPending --> Failed: shipment deadline expires
    Shipped --> Completed: buyer confirms delivery
    Shipped --> Completed: seven-day confirmation deadline expires
    Completed --> [*]
    Failed --> [*]
~~~

## Transition rules

- Transitions are business commands, not unrestricted status updates.
- Every transition validates source state, actor, verification, account restrictions, and server time.
- Automatic transitions are claimed from durable database state and safe to repeat after restart.
- Public projections are emitted only after the corresponding transition commits.
