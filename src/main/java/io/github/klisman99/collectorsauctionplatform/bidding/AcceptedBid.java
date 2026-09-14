package io.github.klisman99.collectorsauctionplatform.bidding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accepted_bids")
class AcceptedBid {

  @Id private UUID id;

  @Column(name = "auction_id", nullable = false, updatable = false)
  private UUID auctionId;

  @Column(name = "bidder_id", nullable = false, updatable = false)
  private UUID bidderId;

  @Column(name = "amount_cents", nullable = false, updatable = false)
  private long amountCents;

  @Column(name = "sequence_number", nullable = false, updatable = false)
  private long sequence;

  @Column(name = "accepted_at", nullable = false, updatable = false)
  private Instant acceptedAt;

  @Column(name = "bidder_pseudonym", nullable = false, updatable = false)
  private String bidderPseudonym;

  protected AcceptedBid() {}

  AcceptedBid(
      UUID auctionId,
      UUID bidderId,
      long amountCents,
      long sequence,
      Instant acceptedAt,
      String bidderPseudonym) {
    this.id = UUID.randomUUID();
    this.auctionId = auctionId;
    this.bidderId = bidderId;
    this.amountCents = amountCents;
    this.sequence = sequence;
    this.acceptedAt = acceptedAt;
    this.bidderPseudonym = bidderPseudonym;
  }

  long amountCents() {
    return amountCents;
  }

  long sequence() {
    return sequence;
  }

  Instant acceptedAt() {
    return acceptedAt;
  }

  String bidderPseudonym() {
    return bidderPseudonym;
  }
}
