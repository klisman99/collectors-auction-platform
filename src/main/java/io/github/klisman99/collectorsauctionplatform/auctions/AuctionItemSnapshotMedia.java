package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
class AuctionItemSnapshotMedia {

  @Column(name = "media_id", nullable = false, updatable = false)
  private UUID mediaId;

  @Column(name = "content_type", nullable = false, updatable = false)
  private String contentType;

  @Column(name = "sort_order", nullable = false, updatable = false)
  private int sortOrder;

  @Column(nullable = false, updatable = false, columnDefinition = "bytea")
  private byte[] content;

  protected AuctionItemSnapshotMedia() {}

  static AuctionItemSnapshotMedia from(CatalogAuctioning.SnapshotMedia source) {
    AuctionItemSnapshotMedia snapshot = new AuctionItemSnapshotMedia();
    snapshot.mediaId = source.mediaId();
    snapshot.contentType = source.contentType();
    snapshot.sortOrder = source.sortOrder();
    snapshot.content = source.content().clone();
    return snapshot;
  }

  UUID mediaId() {
    return mediaId;
  }

  String contentType() {
    return contentType;
  }

  int sortOrder() {
    return sortOrder;
  }

  byte[] content() {
    return content.clone();
  }
}
