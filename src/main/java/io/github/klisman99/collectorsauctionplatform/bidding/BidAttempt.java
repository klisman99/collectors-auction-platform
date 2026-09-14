package io.github.klisman99.collectorsauctionplatform.bidding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bid_attempts")
class BidAttempt {

  @Id private UUID id;

  @Column(name = "auction_id", nullable = false, updatable = false)
  private UUID auctionId;

  @Column(name = "bidder_id", nullable = false, updatable = false)
  private UUID bidderId;

  @Column(name = "idempotency_key", nullable = false, updatable = false)
  private UUID idempotencyKey;

  @Column(name = "amount_cents", nullable = false, updatable = false)
  private long amountCents;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private BidCommandResult.Status status;

  @Column(name = "outcome_code", nullable = false, updatable = false)
  private String outcomeCode;

  @Column(name = "required_amount_cents", updatable = false)
  private Long requiredAmountCents;

  @Column(name = "accepted_sequence", updatable = false)
  private Long acceptedSequence;

  @Column(name = "accepted_at", updatable = false)
  private Instant acceptedAt;

  @Column(name = "bidder_pseudonym", updatable = false)
  private String bidderPseudonym;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  protected BidAttempt() {}

  BidAttempt(
      UUID auctionId,
      UUID bidderId,
      UUID idempotencyKey,
      long amountCents,
      BidCommandResult result,
      Instant receivedAt) {
    this(auctionId, bidderId, idempotencyKey, amountCents, result.status(), result, receivedAt);
  }

  BidAttempt(
      UUID auctionId,
      UUID bidderId,
      UUID idempotencyKey,
      long amountCents,
      BidCommandResult.Status storedStatus,
      BidCommandResult result,
      Instant receivedAt) {
    this.id = UUID.randomUUID();
    this.auctionId = auctionId;
    this.bidderId = bidderId;
    this.idempotencyKey = idempotencyKey;
    this.amountCents = amountCents;
    this.status = storedStatus;
    this.outcomeCode = result.code();
    this.requiredAmountCents = result.requiredAmountCents();
    this.acceptedSequence = result.sequence();
    this.acceptedAt = result.acceptedAt();
    this.bidderPseudonym = result.bidderPseudonym();
    this.receivedAt = receivedAt;
  }

  long amountCents() {
    return amountCents;
  }

  boolean isOriginalResult() {
    return status == BidCommandResult.Status.ACCEPTED
        || status == BidCommandResult.Status.REJECTED
        || status == BidCommandResult.Status.RATE_LIMITED;
  }

  BidCommandResult result(BidCommandResult.Status returnedStatus) {
    return new BidCommandResult(
        returnedStatus,
        outcomeCode,
        amountCents,
        requiredAmountCents,
        acceptedSequence,
        bidderPseudonym,
        acceptedAt);
  }

  BidCommandResult replayedResult() {
    return result(
        status == BidCommandResult.Status.ACCEPTED ? BidCommandResult.Status.DEDUPLICATED : status);
  }
}
