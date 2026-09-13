package io.github.klisman99.collectorsauctionplatform.bidding;

import java.time.Instant;

public record BidCommandResult(
    Status status,
    String code,
    long amountCents,
    Long requiredAmountCents,
    Long sequence,
    String bidderPseudonym,
    Instant acceptedAt) {

  public enum Status {
    ACCEPTED,
    REJECTED,
    DEDUPLICATED,
    RATE_LIMITED,
    IDEMPOTENCY_CONFLICT
  }
}
