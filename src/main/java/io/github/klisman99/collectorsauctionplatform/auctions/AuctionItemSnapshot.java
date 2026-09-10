package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class AuctionItemSnapshot {

  @Column(name = "snapshot_category", nullable = false, updatable = false)
  private String category;

  @Column(name = "snapshot_other_category_label", updatable = false)
  private String otherCategoryLabel;

  @Column(name = "snapshot_title", nullable = false, updatable = false)
  private String title;

  @Column(name = "snapshot_description", nullable = false, updatable = false)
  private String description;

  @Column(name = "snapshot_condition", nullable = false, updatable = false)
  private String condition;

  @Column(name = "snapshot_condition_notes", nullable = false, updatable = false)
  private String conditionNotes;

  @Column(name = "snapshot_ownership_declared", nullable = false, updatable = false)
  private boolean ownershipDeclared;

  protected AuctionItemSnapshot() {}

  static AuctionItemSnapshot from(CatalogAuctioning.ItemSnapshot source) {
    AuctionItemSnapshot snapshot = new AuctionItemSnapshot();
    snapshot.category = source.category();
    snapshot.otherCategoryLabel = source.otherCategoryLabel();
    snapshot.title = source.title();
    snapshot.description = source.description();
    snapshot.condition = source.condition();
    snapshot.conditionNotes = source.conditionNotes();
    snapshot.ownershipDeclared = source.ownershipDeclared();
    return snapshot;
  }

  String category() {
    return category;
  }

  String otherCategoryLabel() {
    return otherCategoryLabel;
  }

  String title() {
    return title;
  }

  String description() {
    return description;
  }

  String condition() {
    return condition;
  }

  String conditionNotes() {
    return conditionNotes;
  }

  boolean ownershipDeclared() {
    return ownershipDeclared;
  }
}
