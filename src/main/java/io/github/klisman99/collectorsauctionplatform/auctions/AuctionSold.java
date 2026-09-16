package io.github.klisman99.collectorsauctionplatform.auctions;

import java.time.Instant;
import java.util.UUID;

/** Immutable sold-auction fact used to create the exactly-one settlement sale before commit. */
public record AuctionSold(
    UUID auctionId,
    UUID itemId,
    UUID sellerId,
    UUID buyerId,
    long amountCents,
    Instant occurredAt) {}
