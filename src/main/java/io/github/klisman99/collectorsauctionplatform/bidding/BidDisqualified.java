package io.github.klisman99.collectorsauctionplatform.bidding;

import java.time.Instant;
import java.util.UUID;

/** Durable fact that an accepted bid permanently became ineligible after account suspension. */
public record BidDisqualified(
    UUID acceptedBidId,
    UUID auctionId,
    UUID bidderId,
    UUID actorId,
    String reasonCategory,
    String publicReason,
    String internalNote,
    Instant disqualifiedAt) {}
