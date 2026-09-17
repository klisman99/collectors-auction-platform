package io.github.klisman99.collectorsauctionplatform.bidding;

import java.time.Instant;
import java.util.UUID;

public record BidAccepted(
    UUID auctionId,
    UUID bidderId,
    long amountCents,
    long sequence,
    long projectionVersion,
    String bidderPseudonym,
    Instant acceptedAt,
    long currentAmountCents,
    long nextMinimumAmountCents,
    boolean reserveMet,
    Instant effectiveEndAt) {}
