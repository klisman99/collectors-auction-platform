ALTER TABLE auctions ADD COLUMN current_amount_cents BIGINT;
ALTER TABLE auctions ADD COLUMN accepted_bid_count BIGINT NOT NULL DEFAULT 0;
UPDATE auctions SET current_amount_cents = opening_amount_cents;
ALTER TABLE auctions ALTER COLUMN current_amount_cents SET NOT NULL;
ALTER TABLE auctions ADD CONSTRAINT auctions_current_amount_check CHECK (
    current_amount_cents BETWEEN opening_amount_cents AND 100000000
    AND accepted_bid_count >= 0
);

CREATE TABLE bidder_pseudonyms (
    id UUID PRIMARY KEY,
    auction_id UUID NOT NULL REFERENCES auctions (id),
    bidder_id UUID NOT NULL REFERENCES regular_accounts (id),
    pseudonym VARCHAR(32) NOT NULL,
    CONSTRAINT bidder_pseudonyms_bidder_key UNIQUE (auction_id, bidder_id),
    CONSTRAINT bidder_pseudonyms_public_key UNIQUE (auction_id, pseudonym)
);

CREATE TABLE accepted_bids (
    id UUID PRIMARY KEY,
    auction_id UUID NOT NULL REFERENCES auctions (id),
    bidder_id UUID NOT NULL REFERENCES regular_accounts (id),
    amount_cents BIGINT NOT NULL CHECK (amount_cents BETWEEN 1000 AND 100000000),
    sequence_number BIGINT NOT NULL CHECK (sequence_number > 0),
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    bidder_pseudonym VARCHAR(32) NOT NULL,
    CONSTRAINT accepted_bids_sequence_key UNIQUE (auction_id, sequence_number)
);
CREATE INDEX accepted_bids_public_history_idx
    ON accepted_bids (auction_id, sequence_number DESC);

CREATE TABLE bid_attempts (
    id UUID PRIMARY KEY,
    auction_id UUID NOT NULL,
    bidder_id UUID NOT NULL REFERENCES regular_accounts (id),
    idempotency_key UUID NOT NULL,
    amount_cents BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    outcome_code VARCHAR(64) NOT NULL,
    required_amount_cents BIGINT,
    accepted_sequence BIGINT,
    accepted_at TIMESTAMP WITH TIME ZONE,
    bidder_pseudonym VARCHAR(32),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT bid_attempts_status_check CHECK (status IN (
        'ACCEPTED', 'REJECTED', 'DEDUPLICATED', 'RATE_LIMITED', 'IDEMPOTENCY_CONFLICT'
    ))
);
CREATE INDEX bid_attempts_idempotency_idx
    ON bid_attempts (auction_id, bidder_id, idempotency_key, received_at);
CREATE INDEX bid_attempts_rate_limit_idx
    ON bid_attempts (auction_id, bidder_id, received_at);
