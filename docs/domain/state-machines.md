# State machines

Item, auction, and sale have independent lifecycles. Combining them in one status field would make valid transitions ambiguous.

## Collectible item

~~~mermaid
stateDiagram-v2
    [*] --> Draft
    Draft --> UnderReview: submit
    UnderReview --> Approved: approve
    UnderReview --> Draft: reject with reason
    Approved --> UnderReview: material change
    Approved --> Archived: archive
    Archived --> [*]
~~~

## Auction

An extension updates the effective end time while the auction remains live.

~~~mermaid
stateDiagram-v2
    [*] --> Draft
    Draft --> Scheduled: schedule
    Scheduled --> Live: start time reached
    Draft --> Cancelled: cancel
    Scheduled --> Cancelled: cancel before start
    Live --> Closing: effective end reached
    Live --> Suspended: moderator suspends
    Suspended --> Live: moderator resumes
    Suspended --> Cancelled: cancel
    Closing --> Sold: reserve met
    Closing --> Unsold: no bids
    Closing --> AwaitingSellerDecision: below reserve
    AwaitingSellerDecision --> Sold: seller accepts
    AwaitingSellerDecision --> Unsold: reject or expire
    Sold --> [*]
    Unsold --> [*]
    Cancelled --> [*]
~~~

## Sale

~~~mermaid
stateDiagram-v2
    [*] --> PaymentPending
    PaymentPending --> Paid: simulate payment
    Paid --> ShippingPending: request shipment
    ShippingPending --> Shipped: simulate shipment
    Shipped --> Delivered: simulate delivery
    Delivered --> Completed: confirm completion
    Completed --> [*]
~~~

## Notes

- State transitions are business commands, not unrestricted field updates.
- Every transition validates its source state and actor.
- Repeated system commands such as closing must be safe.
- A later dispute workflow may extend the sale machine without changing the MVP auction outcome.
