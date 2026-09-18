package io.github.klisman99.collectorsauctionplatform.auctions;

import java.time.Instant;
import java.util.UUID;

/** Durable fact emitted after an auction lifecycle transition is persisted. */
public record AuctionLifecycleEvent(
    UUID auctionId,
    UUID itemId,
    UUID sellerId,
    String sellerEmail,
    String itemTitle,
    Type type,
    long projectionVersion,
    UUID operationalActorId,
    SuspensionReasonCategory reasonCategory,
    String publicReason,
    String internalNote,
    AdministrativeItemDisposition itemDisposition,
    Instant occurredAt,
    PublishedTerms previousTerms,
    PublishedTerms currentTerms,
    Long finalAmountCents,
    UUID winningBidderId,
    String winningBidderHandle,
    String winningBidderEmail,
    Instant sellerDecisionDeadlineAt) {

  public record PublishedTerms(Long reserveAmountCents, Instant startsAt, Instant endsAt) {}

  public enum Type {
    SCHEDULED,
    RESCHEDULED,
    STARTED,
    CANCELLED,
    ENDED,
    SOLD,
    UNSOLD,
    AWAITING_SELLER_DECISION,
    SELLER_DECISION_ACCEPTED,
    SELLER_DECISION_REJECTED,
    SELLER_DECISION_EXPIRED,
    SELLER_DECISION_REOPENED,
    SELLER_DECISION_NO_ELIGIBLE_BID,
    SUSPENDED,
    RELEASED,
    RESUMED,
    ADMINISTRATIVELY_CANCELLED
  }
}
