ALTER TABLE collectible_item_media RENAME COLUMN storage_key TO display_storage_key;
ALTER TABLE collectible_item_media ADD COLUMN thumbnail_storage_key VARCHAR(300);
UPDATE collectible_item_media SET thumbnail_storage_key = display_storage_key || '.thumbnail';
ALTER TABLE collectible_item_media ALTER COLUMN thumbnail_storage_key SET NOT NULL;
ALTER TABLE collectible_item_media ADD CONSTRAINT collectible_item_media_item_sort_order_key UNIQUE (item_id, sort_order);
