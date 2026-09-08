package io.github.klisman99.collectorsauctionplatform.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "collectible_item_media",
    uniqueConstraints =
        @UniqueConstraint(
            name = "collectible_item_media_item_sort_order_key",
            columnNames = {"item_id", "sort_order"}))
class CollectibleItemMedia {

  @Id private UUID id;

  @Column(name = "item_id", nullable = false, updatable = false)
  private UUID itemId;

  @Column(name = "display_storage_key", nullable = false, unique = true)
  private String displayStorageKey;

  @Column(name = "thumbnail_storage_key", nullable = false, unique = true)
  private String thumbnailStorageKey;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "byte_size", nullable = false)
  private long byteSize;

  @Column(name = "display_width", nullable = false)
  private int displayWidth;

  @Column(name = "display_height", nullable = false)
  private int displayHeight;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CollectibleItemMedia() {}

  static CollectibleItemMedia create(
      UUID itemId,
      String displayStorageKey,
      String thumbnailStorageKey,
      String contentType,
      long byteSize,
      int displayWidth,
      int displayHeight,
      int sortOrder,
      Instant createdAt) {

    CollectibleItemMedia media = new CollectibleItemMedia();
    media.id = UUID.randomUUID();
    media.itemId = itemId;
    media.displayStorageKey = displayStorageKey;
    media.thumbnailStorageKey = thumbnailStorageKey;
    media.contentType = contentType;
    media.byteSize = byteSize;
    media.displayWidth = displayWidth;
    media.displayHeight = displayHeight;
    media.sortOrder = sortOrder;
    media.createdAt = createdAt;
    return media;
  }

  UUID id() {
    return id;
  }

  UUID itemId() {
    return itemId;
  }

  String displayStorageKey() {
    return displayStorageKey;
  }

  String thumbnailStorageKey() {
    return thumbnailStorageKey;
  }

  String contentType() {
    return contentType;
  }

  int sortOrder() {
    return sortOrder;
  }

  void sortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
