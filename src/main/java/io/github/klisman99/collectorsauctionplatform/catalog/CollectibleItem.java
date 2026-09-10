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
    DRAFT,
    UNDER_REVIEW,
    APPROVED
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

  @Column(name = "submission_reason")
  private String submissionReason;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "auction_locked_at")
  private Instant auctionLockedAt;

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
    if (status == Status.APPROVED) {
      status = Status.DRAFT;
    }
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

  Status status() {
    return status;
  }

  String submissionReason() {
    return submissionReason;
  }

  Instant submittedAt() {
    return submittedAt;
  }

  boolean isAuctionLocked() {
    return auctionLockedAt != null;
  }

  void lockForAuction(Instant now) {
    auctionLockedAt = now;
  }

  void submit(Instant now) {
    status = Status.UNDER_REVIEW;
    submissionReason = null;
    submittedAt = now;
    updatedAt = now;
  }

  void approve(Instant now) {
    status = Status.APPROVED;
    submissionReason = null;
    updatedAt = now;
  }

  void invalidateApproval(Instant now) {
    if (status == Status.APPROVED) {
      status = Status.DRAFT;
      submissionReason = null;
      submittedAt = null;
      updatedAt = now;
    }
  }

  void reject(String reason, Instant now) {
    status = Status.DRAFT;
    submissionReason = reason;
    updatedAt = now;
  }
}
