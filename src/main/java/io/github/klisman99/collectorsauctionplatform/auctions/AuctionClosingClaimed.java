package io.github.klisman99.collectorsauctionplatform.auctions;

import java.time.Instant;
import java.util.UUID;

/** Durable request for bidding to select and record the one eligible closing outcome. */
public record AuctionClosingClaimed(UUID auctionId, Instant claimedAt) {}
