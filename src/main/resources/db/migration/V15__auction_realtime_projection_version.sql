ALTER TABLE auctions
    ADD COLUMN projection_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE auctions
    ADD CONSTRAINT chk_auctions_projection_version_positive CHECK (projection_version > 0);
