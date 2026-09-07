package io.github.klisman99.collectorsauctionplatform.catalog;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "collectible_item_media", uniqueConstraints = @UniqueConstraint(name = "collectible_item_media_item_sort_order_key", columnNames = {"item_id", "sort_order"}))
class CollectibleItemMedia {
    @Id private UUID id;
    @Column(name="item_id", nullable=false, updatable=false) private UUID itemId;
    @Column(name="display_storage_key", nullable=false, unique=true) private String displayStorageKey;
    @Column(name="thumbnail_storage_key", nullable=false, unique=true) private String thumbnailStorageKey;
    @Column(name="content_type", nullable=false) private String contentType;
    @Column(name="byte_size", nullable=false) private long byteSize;
    @Column(name="display_width", nullable=false) private int displayWidth;
    @Column(name="display_height", nullable=false) private int displayHeight;
    @Column(name="sort_order", nullable=false) private int sortOrder;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    protected CollectibleItemMedia() {}
    static CollectibleItemMedia create(UUID itemId, String displayKey, String thumbnailKey, String type, long size, int width, int height, int order, Instant now) {
        CollectibleItemMedia m = new CollectibleItemMedia(); m.id=UUID.randomUUID(); m.itemId=itemId; m.displayStorageKey=displayKey; m.thumbnailStorageKey=thumbnailKey; m.contentType=type; m.byteSize=size; m.displayWidth=width; m.displayHeight=height; m.sortOrder=order; m.createdAt=now; return m;
    }
    UUID id(){return id;} UUID itemId(){return itemId;} String displayStorageKey(){return displayStorageKey;} String thumbnailStorageKey(){return thumbnailStorageKey;} String contentType(){return contentType;} int sortOrder(){return sortOrder;}
    void sortOrder(int value){sortOrder=value;}
}
