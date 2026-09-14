package io.github.klisman99.collectorsauctionplatform.bidding;

import java.time.Instant;
import java.util.UUID;

public record BidAccepted(
    UUID auctionId,
    UUID bidderId,
    long amountCents,
    long sequence,
    String bidderPseudonym,
    Instant acceptedAt) {}
