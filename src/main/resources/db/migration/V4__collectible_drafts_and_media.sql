CREATE TABLE collectible_items (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES regular_accounts (id),
    category VARCHAR(32) NOT NULL,
    other_category_label VARCHAR(80),
    title VARCHAR(120) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    condition VARCHAR(32) NOT NULL,
    condition_notes VARCHAR(2000) NOT NULL,
    ownership_declared BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT collectible_items_other_category_check CHECK ((category = 'OTHER') = (other_category_label IS NOT NULL)),
    CONSTRAINT collectible_items_ownership_check CHECK (ownership_declared = TRUE)
);
CREATE INDEX collectible_items_owner_idx ON collectible_items (owner_id, updated_at);

CREATE TABLE collectible_item_media (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES collectible_items (id) ON DELETE CASCADE,
    storage_key VARCHAR(300) NOT NULL UNIQUE,
    content_type VARCHAR(32) NOT NULL,
    byte_size BIGINT NOT NULL,
    display_width INT NOT NULL,
    display_height INT NOT NULL,
    sort_order INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX collectible_item_media_item_idx ON collectible_item_media (item_id, sort_order);
