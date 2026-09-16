ALTER TABLE auctions ADD COLUMN eligible_bid_count BIGINT NOT NULL DEFAULT 0;
UPDATE auctions SET eligible_bid_count = accepted_bid_count;
ALTER TABLE auctions ADD CONSTRAINT auctions_eligible_bid_count_check CHECK (eligible_bid_count >= 0);

CREATE TABLE bid_disqualifications (
    id UUID PRIMARY KEY,
    accepted_bid_id UUID NOT NULL REFERENCES accepted_bids (id),
    actor_id UUID NOT NULL,
    reason_category VARCHAR(32) NOT NULL,
    public_reason VARCHAR(500) NOT NULL,
    internal_note VARCHAR(2000),
    disqualified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT bid_disqualifications_accepted_bid_key UNIQUE (accepted_bid_id)
);

CREATE INDEX bid_disqualifications_disqualified_at_idx
    ON bid_disqualifications (disqualified_at DESC);

ALTER TABLE auction_timeline_events DROP CONSTRAINT auction_timeline_reason_category_check;
ALTER TABLE auction_timeline_events ADD CONSTRAINT auction_timeline_reason_category_check CHECK (
    reason_category IS NULL OR reason_category IN (
        'POLICY_REVIEW', 'SECURITY', 'ITEM_CONCERN', 'ACCOUNT_SUSPENSION', 'OTHER'
    )
);
