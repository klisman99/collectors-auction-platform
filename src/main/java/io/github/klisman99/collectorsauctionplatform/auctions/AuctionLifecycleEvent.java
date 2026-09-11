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
    String publicReason,
    Instant occurredAt,
    PublishedTerms previousTerms,
    PublishedTerms currentTerms) {

  public record PublishedTerms(Long reserveAmountCents, Instant startsAt, Instant endsAt) {}

  public enum Type {
    SCHEDULED,
    RESCHEDULED,
    STARTED,
    CANCELLED,
    ENDED
  }
}
