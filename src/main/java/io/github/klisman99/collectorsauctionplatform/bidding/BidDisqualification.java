package io.github.klisman99.collectorsauctionplatform.bidding;

import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountSuspension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bid_disqualifications")
class BidDisqualification {

  @Id private UUID id;

  @Column(name = "accepted_bid_id", nullable = false, updatable = false)
  private UUID acceptedBidId;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "reason_category", nullable = false, updatable = false)
  private String reasonCategory;

  @Column(name = "public_reason", nullable = false, updatable = false)
  private String publicReason;

  @Column(name = "internal_note", updatable = false, length = 2000)
  private String internalNote;

  @Column(name = "disqualified_at", nullable = false, updatable = false)
  private Instant disqualifiedAt;

  protected BidDisqualification() {}

  private BidDisqualification(AcceptedBid bid, RegularAccountSuspension suspension) {
    this.id = UUID.randomUUID();
    this.acceptedBidId = bid.id();
    this.actorId = suspension.actorId();
    this.reasonCategory = suspension.reasonCategory().name();
    this.publicReason = suspension.publicReason();
    this.internalNote = suspension.internalNote();
    this.disqualifiedAt = suspension.occurredAt();
  }

  static BidDisqualification forAccountSuspension(
      AcceptedBid bid, RegularAccountSuspension suspension) {
    return new BidDisqualification(bid, suspension);
  }

  UUID acceptedBidId() {
    return acceptedBidId;
  }

  UUID actorId() {
    return actorId;
  }

  String reasonCategory() {
    return reasonCategory;
  }

  String publicReason() {
    return publicReason;
  }

  String internalNote() {
    return internalNote;
  }

  Instant disqualifiedAt() {
    return disqualifiedAt;
  }
}
