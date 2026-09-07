package io.github.klisman99.collectorsauctionplatform.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collectible_items")
class CollectibleItem {

  enum Category {
    CARDS,
    COINS_AND_CURRENCY,
    STAMPS,
    COMICS_AND_BOOKS,
    TOYS_AND_FIGURES,
    MEMORABILIA,
    ART_AND_ANTIQUES,
    OTHER
  }

  enum Condition {
    NEW_SEALED,
    EXCELLENT,
    VERY_GOOD,
    GOOD,
    FAIR,
    POOR,
    NOT_APPLICABLE
  }

  enum Status {
    DRAFT
  }

  @Id private UUID id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Category category;

  @Column(name = "other_category_label")
  private String otherCategoryLabel;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Condition condition;

  @Column(name = "condition_notes", nullable = false)
  private String conditionNotes;

  @Column(name = "ownership_declared", nullable = false)
  private boolean ownershipDeclared;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CollectibleItem() {}

  static CollectibleItem create(UUID ownerId, DraftRequest request, Instant now) {
    CollectibleItem item = new CollectibleItem();
    item.id = UUID.randomUUID();
    item.ownerId = ownerId;
    item.status = Status.DRAFT;
    item.createdAt = now;
    item.apply(request, now);
    return item;
  }

  void apply(DraftRequest request, Instant now) {
    this.category = request.category();
    this.otherCategoryLabel = request.otherCategoryLabel();
    this.title = request.title();
    this.description = request.description();
    this.condition = request.condition();
    this.conditionNotes = request.conditionNotes();
    this.ownershipDeclared = request.ownershipDeclared();
    this.updatedAt = now;
  }

  UUID id() {
    return id;
  }

  UUID ownerId() {
    return ownerId;
  }

  Category category() {
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

  Condition condition() {
    return condition;
  }

  String conditionNotes() {
    return conditionNotes;
  }

  boolean ownershipDeclared() {
    return ownershipDeclared;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }
}
