package io.github.klisman99.collectorsauctionplatform.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collectible_item_reviews")
class CollectibleItemReview {

  enum Decision {
    APPROVED,
    REJECTED
  }

  @Id private UUID id;

  @Column(name = "item_id", nullable = false, updatable = false)
  private UUID itemId;

  @Column(name = "reviewer_id", nullable = false, updatable = false)
  private UUID reviewerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private Decision decision;

  @Column(name = "public_reason", updatable = false)
  private String publicReason;

  @Column(name = "internal_note", updatable = false)
  private String internalNote;

  @Column(name = "decided_at", nullable = false, updatable = false)
  private Instant decidedAt;

  protected CollectibleItemReview() {}

  static CollectibleItemReview create(
      UUID itemId,
      UUID reviewerId,
      Decision decision,
      String publicReason,
      String internalNote,
      Instant decidedAt) {
    CollectibleItemReview review = new CollectibleItemReview();
    review.id = UUID.randomUUID();
    review.itemId = itemId;
    review.reviewerId = reviewerId;
    review.decision = decision;
    review.publicReason = publicReason;
    review.internalNote = internalNote;
    review.decidedAt = decidedAt;
    return review;
  }
}
