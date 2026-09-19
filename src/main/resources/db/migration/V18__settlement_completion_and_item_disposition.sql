ALTER TABLE sales ADD COLUMN delivery_confirmation_deadline_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE sales ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE sales ADD COLUMN terminal_reason VARCHAR(64);

ALTER TABLE sales ADD COLUMN item_disposition VARCHAR(32);

ALTER TABLE sales ADD CONSTRAINT sales_delivery_confirmation_deadline_check CHECK (
    state NOT IN ('SHIPPED', 'COMPLETED') OR delivery_confirmation_deadline_at IS NOT NULL
);

ALTER TABLE sales ADD CONSTRAINT sales_terminal_disposition_check CHECK (
    (
        state = 'FAILED'
        AND failed_at IS NOT NULL
        AND completed_at IS NULL
        AND terminal_reason IN ('PAYMENT_DEADLINE_EXPIRED', 'SHIPMENT_DEADLINE_EXPIRED')
        AND item_disposition = 'RELISTING_ELIGIBLE'
    )
    OR (
        state = 'COMPLETED'
        AND failed_at IS NULL
        AND completed_at IS NOT NULL
        AND terminal_reason IN ('BUYER_CONFIRMED_DELIVERY', 'DELIVERY_CONFIRMATION_DEADLINE_EXPIRED')
        AND item_disposition = 'ARCHIVED'
    )
    OR (
        state NOT IN ('FAILED', 'COMPLETED')
        AND completed_at IS NULL
        AND terminal_reason IS NULL
        AND item_disposition IS NULL
    )
);

CREATE INDEX sales_state_delivery_confirmation_deadline_idx
    ON sales (state, delivery_confirmation_deadline_at);
