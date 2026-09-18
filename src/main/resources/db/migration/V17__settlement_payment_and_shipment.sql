ALTER TABLE sales ADD COLUMN item_title VARCHAR(200);

ALTER TABLE sales ADD COLUMN seller_handle VARCHAR(30);

ALTER TABLE sales ADD COLUMN buyer_handle VARCHAR(30);

ALTER TABLE sales ADD COLUMN state VARCHAR(32);

ALTER TABLE sales ADD COLUMN payment_deadline_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE sales ADD COLUMN shipment_deadline_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE sales ADD COLUMN paid_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE sales ADD COLUMN shipped_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE sales ADD COLUMN failed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE sales ADD COLUMN carrier VARCHAR(120);

ALTER TABLE sales ADD COLUMN tracking_reference VARCHAR(160);

UPDATE sales
SET item_title = 'Archived sold collectible',
    seller_handle = 'archived-seller',
    buyer_handle = 'archived-buyer',
    state = 'PAYMENT_PENDING',
    payment_deadline_at = created_at + INTERVAL '24' HOUR;

ALTER TABLE sales ALTER COLUMN item_title SET NOT NULL;

ALTER TABLE sales ALTER COLUMN seller_handle SET NOT NULL;

ALTER TABLE sales ALTER COLUMN buyer_handle SET NOT NULL;

ALTER TABLE sales ALTER COLUMN state SET NOT NULL;

ALTER TABLE sales ALTER COLUMN payment_deadline_at SET NOT NULL;

ALTER TABLE sales ADD CONSTRAINT sales_state_check CHECK (
    state IN ('PAYMENT_PENDING', 'SHIPMENT_PENDING', 'SHIPPED', 'COMPLETED', 'FAILED')
);

ALTER TABLE sales ADD CONSTRAINT sales_payment_pending_deadline_check CHECK (
    state <> 'PAYMENT_PENDING' OR payment_deadline_at IS NOT NULL
);

ALTER TABLE sales ADD CONSTRAINT sales_shipment_pending_deadline_check CHECK (
    state <> 'SHIPMENT_PENDING' OR shipment_deadline_at IS NOT NULL
);

ALTER TABLE sales ADD CONSTRAINT sales_payment_pending_fields_check CHECK (
    state <> 'PAYMENT_PENDING' OR (
        paid_at IS NULL
        AND shipment_deadline_at IS NULL
        AND shipped_at IS NULL
        AND failed_at IS NULL
        AND carrier IS NULL
        AND tracking_reference IS NULL
    )
);

ALTER TABLE sales ADD CONSTRAINT sales_shipment_pending_fields_check CHECK (
    state <> 'SHIPMENT_PENDING' OR (
        paid_at IS NOT NULL
        AND shipment_deadline_at IS NOT NULL
        AND shipped_at IS NULL
        AND failed_at IS NULL
        AND carrier IS NULL
        AND tracking_reference IS NULL
    )
);

ALTER TABLE sales ADD CONSTRAINT sales_shipped_fields_check CHECK (
    state NOT IN ('SHIPPED', 'COMPLETED') OR (
        paid_at IS NOT NULL
        AND shipment_deadline_at IS NOT NULL
        AND shipped_at IS NOT NULL
        AND failed_at IS NULL
        AND carrier IS NOT NULL
        AND carrier <> ''
        AND tracking_reference IS NOT NULL
        AND tracking_reference <> ''
    )
);

ALTER TABLE sales ADD CONSTRAINT sales_failed_fields_check CHECK (
    state <> 'FAILED' OR failed_at IS NOT NULL
);

CREATE INDEX sales_state_payment_deadline_idx ON sales (state, payment_deadline_at);

CREATE INDEX sales_state_shipment_deadline_idx ON sales (state, shipment_deadline_at);
