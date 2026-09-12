ALTER TABLE auctions ADD COLUMN suspension_source_state VARCHAR(32);
ALTER TABLE auctions ADD COLUMN suspended_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE auctions ADD COLUMN remaining_duration_millis BIGINT;

ALTER TABLE auctions DROP CONSTRAINT auctions_state_check;
ALTER TABLE auctions ADD CONSTRAINT auctions_state_check CHECK (state IN (
    'DRAFT', 'SCHEDULED', 'LIVE', 'SUSPENDED', 'CLOSING', 'AWAITING_SELLER_DECISION',
    'SOLD', 'UNSOLD', 'CANCELLED'
));
ALTER TABLE auctions DROP CONSTRAINT auctions_active_item_check;
ALTER TABLE auctions ADD CONSTRAINT auctions_active_item_check CHECK (
    (state IN ('DRAFT', 'SOLD', 'UNSOLD', 'CANCELLED') AND active_item_id IS NULL)
    OR (state NOT IN ('DRAFT', 'SOLD', 'UNSOLD', 'CANCELLED') AND active_item_id = item_id)
);
ALTER TABLE auctions ADD CONSTRAINT auctions_suspension_check CHECK (
    (state = 'SUSPENDED' AND suspended_at IS NOT NULL AND (
        (suspension_source_state = 'SCHEDULED' AND remaining_duration_millis IS NULL)
        OR (suspension_source_state = 'LIVE' AND remaining_duration_millis IS NOT NULL AND remaining_duration_millis >= 0)
    ))
    OR (state <> 'SUSPENDED' AND suspension_source_state IS NULL AND suspended_at IS NULL AND remaining_duration_millis IS NULL)
);

ALTER TABLE auction_timeline_events ADD COLUMN reason_category VARCHAR(32);
ALTER TABLE auction_timeline_events ADD COLUMN internal_note VARCHAR(2000);
ALTER TABLE auction_timeline_events ADD COLUMN item_disposition VARCHAR(32);
ALTER TABLE auction_timeline_events DROP CONSTRAINT auction_timeline_public_reason_check;
ALTER TABLE auction_timeline_events ADD CONSTRAINT auction_timeline_public_reason_check CHECK (
    (event_type IN ('CANCELLED', 'SUSPENDED') AND public_reason IS NOT NULL AND LENGTH(TRIM(public_reason)) > 0)
    OR (event_type NOT IN ('CANCELLED', 'SUSPENDED') AND public_reason IS NULL)
);
ALTER TABLE auction_timeline_events DROP CONSTRAINT auction_timeline_event_type_check;
ALTER TABLE auction_timeline_events ADD CONSTRAINT auction_timeline_event_type_check CHECK (event_type IN (
    'SCHEDULED', 'RESCHEDULED', 'STARTED', 'CANCELLED', 'ENDED', 'SUSPENDED', 'RELEASED', 'RESUMED'
));
ALTER TABLE auction_timeline_events ADD CONSTRAINT auction_timeline_administrative_detail_check CHECK (
    (event_type = 'SUSPENDED' AND reason_category IS NOT NULL)
    OR (item_disposition IS NOT NULL AND event_type = 'CANCELLED' AND reason_category IS NOT NULL)
    OR (event_type <> 'SUSPENDED' AND item_disposition IS NULL)
);
ALTER TABLE auction_timeline_events ADD CONSTRAINT auction_timeline_reason_category_check CHECK (
    reason_category IS NULL OR reason_category IN ('POLICY_REVIEW', 'SECURITY', 'ITEM_CONCERN', 'OTHER')
);
ALTER TABLE auction_timeline_events ADD CONSTRAINT auction_timeline_disposition_check CHECK (
    item_disposition IS NULL OR item_disposition IN ('RELEASE_APPROVED_ITEM', 'REVOKE_APPROVAL_TO_DRAFT')
);

CREATE INDEX auctions_suspension_queue_idx ON auctions (state, suspended_at);
