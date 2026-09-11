ALTER TABLE collectible_items ADD COLUMN auction_locked_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE auctions (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES collectible_items (id),
    active_item_id UUID UNIQUE REFERENCES collectible_items (id),
    seller_id UUID NOT NULL REFERENCES regular_accounts (id),
    seller_handle VARCHAR(30) NOT NULL,
    state VARCHAR(32) NOT NULL,
    opening_amount_cents BIGINT NOT NULL,
    minimum_increment_cents BIGINT NOT NULL,
    reserve_amount_cents BIGINT,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    snapshot_category VARCHAR(32) NOT NULL,
    snapshot_other_category_label VARCHAR(80),
    snapshot_title VARCHAR(120) NOT NULL,
    snapshot_description VARCHAR(5000) NOT NULL,
    snapshot_condition VARCHAR(32) NOT NULL,
    snapshot_condition_notes VARCHAR(2000) NOT NULL,
    snapshot_ownership_declared BOOLEAN NOT NULL,
    policy_auction_type VARCHAR(32) NOT NULL,
    policy_currency VARCHAR(3) NOT NULL,
    policy_minimum_amount_cents BIGINT NOT NULL,
    policy_maximum_amount_cents BIGINT NOT NULL,
    policy_minimum_lead_seconds BIGINT NOT NULL,
    policy_minimum_duration_seconds BIGINT NOT NULL,
    policy_maximum_duration_seconds BIGINT NOT NULL,
    policy_protection_window_seconds BIGINT NOT NULL,
    CONSTRAINT auctions_state_check CHECK (state IN (
        'SCHEDULED', 'LIVE', 'SUSPENDED', 'CLOSING', 'AWAITING_SELLER_DECISION',
        'SOLD', 'UNSOLD', 'CANCELLED'
    )),
    CONSTRAINT auctions_active_item_check CHECK (
        (state IN ('SOLD', 'UNSOLD', 'CANCELLED') AND active_item_id IS NULL)
        OR (state NOT IN ('SOLD', 'UNSOLD', 'CANCELLED') AND active_item_id = item_id)
    ),
    CONSTRAINT auctions_amounts_check CHECK (
        opening_amount_cents BETWEEN 1000 AND 100000000
        AND minimum_increment_cents BETWEEN 1000 AND 100000000
        AND (reserve_amount_cents IS NULL OR reserve_amount_cents BETWEEN 1000 AND 100000000)
        AND (reserve_amount_cents IS NULL OR reserve_amount_cents >= opening_amount_cents)
    ),
    CONSTRAINT auctions_interval_check CHECK (ends_at > starts_at)
);
CREATE INDEX auctions_public_schedule_idx ON auctions (state, starts_at);
CREATE INDEX auctions_seller_idx ON auctions (seller_id, scheduled_at);

CREATE TABLE auction_item_snapshot_media (
    auction_id UUID NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    media_id UUID NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    content BYTEA NOT NULL,
    PRIMARY KEY (auction_id, media_id),
    CONSTRAINT auction_item_snapshot_media_order_key UNIQUE (auction_id, sort_order)
);
