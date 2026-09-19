ALTER TABLE audit_records
    ADD COLUMN source_fingerprint VARCHAR(64);

ALTER TABLE audit_records
    ADD COLUMN auction_id UUID;

ALTER TABLE audit_records
    ADD COLUMN item_id UUID;

ALTER TABLE audit_records
    ADD COLUMN sale_id UUID;

ALTER TABLE audit_records
    ADD COLUMN reason_category VARCHAR(64);

ALTER TABLE audit_records
    ADD COLUMN public_reason VARCHAR(1000);

ALTER TABLE audit_records
    ADD COLUMN internal_note VARCHAR(2000);

ALTER TABLE audit_records
    ADD COLUMN amount_cents BIGINT;

ALTER TABLE audit_records
    ADD COLUMN bidder_pseudonym VARCHAR(32);

UPDATE audit_records
SET source_fingerprint = id::text
WHERE source_fingerprint IS NULL;

ALTER TABLE audit_records
    ALTER COLUMN source_fingerprint SET NOT NULL;

ALTER TABLE audit_records
    ADD CONSTRAINT audit_records_source_fingerprint_unique UNIQUE (source_fingerprint);

UPDATE audit_records
SET auction_id = target_id
WHERE target_type = 'AUCTION';

UPDATE audit_records
SET sale_id = target_id
WHERE target_type = 'SALE';

CREATE TABLE audit_record_participants (
    audit_record_id UUID NOT NULL REFERENCES audit_records (id),
    account_id UUID NOT NULL,
    CONSTRAINT audit_record_participants_pk PRIMARY KEY (audit_record_id, account_id)
);

INSERT INTO audit_record_participants (audit_record_id, account_id)
SELECT id, actor_id
FROM audit_records
WHERE actor_type = 'REGULAR_ACCOUNT'
  AND actor_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO audit_record_participants (audit_record_id, account_id)
SELECT id, target_id
FROM audit_records
WHERE target_type = 'REGULAR_ACCOUNT'
ON CONFLICT DO NOTHING;

CREATE INDEX audit_records_history_order_idx ON audit_records (occurred_at DESC, id DESC);
CREATE INDEX audit_records_auction_history_idx ON audit_records (auction_id, occurred_at DESC, id DESC);
CREATE INDEX audit_record_participants_account_history_idx
    ON audit_record_participants (account_id, audit_record_id);
