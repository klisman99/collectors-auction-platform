ALTER TABLE auctions ADD COLUMN ended_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE auctions ADD COLUMN cancellation_reason VARCHAR(500);

ALTER TABLE auctions ADD CONSTRAINT auctions_cancellation_reason_check CHECK (
    (state = 'CANCELLED' AND cancellation_reason IS NOT NULL AND LENGTH(TRIM(cancellation_reason)) > 0)
    OR (state <> 'CANCELLED' AND cancellation_reason IS NULL)
);
ALTER TABLE auctions ADD CONSTRAINT auctions_terminal_end_check CHECK (
    state NOT IN ('SOLD', 'UNSOLD', 'CANCELLED') OR ended_at IS NOT NULL
);

CREATE TABLE auction_timeline_events (
    auction_id UUID NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    sequence_number INT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    public_reason VARCHAR(500),
    PRIMARY KEY (auction_id, sequence_number),
    CONSTRAINT auction_timeline_public_reason_check CHECK (
        (event_type = 'CANCELLED' AND public_reason IS NOT NULL AND LENGTH(TRIM(public_reason)) > 0)
        OR (event_type <> 'CANCELLED' AND public_reason IS NULL)
    ),
    CONSTRAINT auction_timeline_event_type_check CHECK (event_type IN (
        'SCHEDULED', 'RESCHEDULED', 'STARTED', 'CANCELLED', 'ENDED'
    ))
);

CREATE INDEX auctions_public_end_idx ON auctions (state, ended_at DESC);
