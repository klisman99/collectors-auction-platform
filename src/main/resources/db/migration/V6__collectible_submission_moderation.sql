ALTER TABLE collectible_items ADD COLUMN submission_reason VARCHAR(2000);
ALTER TABLE collectible_items ADD COLUMN submitted_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE collectible_item_reviews (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES collectible_items (id),
    reviewer_id UUID NOT NULL,
    decision VARCHAR(16) NOT NULL,
    public_reason VARCHAR(2000),
    internal_note VARCHAR(2000),
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT collectible_item_reviews_decision_reason_check
        CHECK ((decision = 'REJECTED') = (public_reason IS NOT NULL)),
    CONSTRAINT collectible_item_reviews_item_decision_check CHECK (decision IN ('APPROVED', 'REJECTED'))
);
CREATE INDEX collectible_item_reviews_queue_idx
    ON collectible_item_reviews (decision, decided_at);
