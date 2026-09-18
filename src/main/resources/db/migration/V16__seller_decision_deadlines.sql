ALTER TABLE auctions ADD COLUMN seller_decision_deadline_at TIMESTAMP WITH TIME ZONE;

UPDATE auctions
SET seller_decision_deadline_at = final_outcome_recorded_at + INTERVAL '1' DAY
WHERE state = 'AWAITING_SELLER_DECISION';

ALTER TABLE auctions ADD CONSTRAINT auctions_seller_decision_deadline_check CHECK (
    (state = 'AWAITING_SELLER_DECISION' AND seller_decision_deadline_at IS NOT NULL)
    OR (state <> 'AWAITING_SELLER_DECISION' AND seller_decision_deadline_at IS NULL)
);

CREATE INDEX auctions_seller_decision_deadline_idx
    ON auctions (state, seller_decision_deadline_at);

ALTER TABLE auction_timeline_events DROP CONSTRAINT auction_timeline_event_type_check;
ALTER TABLE auction_timeline_events ADD CONSTRAINT auction_timeline_event_type_check CHECK (
    event_type IN (
        'SCHEDULED',
        'RESCHEDULED',
        'STARTED',
        'CANCELLED',
        'ENDED',
        'SUSPENDED',
        'RELEASED',
        'RESUMED',
        'AWAITING_SELLER_DECISION',
        'SELLER_DECISION_ACCEPTED',
        'SELLER_DECISION_REJECTED',
        'SELLER_DECISION_EXPIRED',
        'SELLER_DECISION_REOPENED',
        'SELLER_DECISION_NO_ELIGIBLE_BID'
    )
);
