ALTER TABLE auctions ADD COLUMN final_bid_id UUID;

ALTER TABLE auctions ADD COLUMN final_bidder_id UUID;

ALTER TABLE auctions ADD COLUMN final_bidder_pseudonym VARCHAR(32);

ALTER TABLE auctions ADD COLUMN final_amount_cents BIGINT;

ALTER TABLE auctions ADD COLUMN final_outcome_recorded_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE auctions ADD CONSTRAINT auctions_final_bid_check CHECK (
    (
        state IN ('SOLD', 'AWAITING_SELLER_DECISION')
        AND final_bid_id IS NOT NULL
        AND final_bidder_id IS NOT NULL
        AND final_bidder_pseudonym IS NOT NULL
        AND final_amount_cents BETWEEN 1000 AND 100000000
        AND final_outcome_recorded_at IS NOT NULL
    )
    OR (
        state NOT IN ('SOLD', 'AWAITING_SELLER_DECISION')
        AND final_bid_id IS NULL
        AND final_bidder_id IS NULL
        AND final_bidder_pseudonym IS NULL
        AND final_amount_cents IS NULL
        AND final_outcome_recorded_at IS NULL
    )
);

CREATE TABLE sales (
    id UUID PRIMARY KEY,
    auction_id UUID NOT NULL UNIQUE,
    item_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    buyer_id UUID NOT NULL,
    amount_cents BIGINT NOT NULL CHECK (amount_cents BETWEEN 1000 AND 100000000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX sales_buyer_idx ON sales (buyer_id, created_at DESC);
CREATE INDEX sales_seller_idx ON sales (seller_id, created_at DESC);
