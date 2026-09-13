package io.github.klisman99.collectorsauctionplatform.auctions;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deliberate auctions-module gate used by bidding while its transaction is active. */
@Service
public class AuctionBidding {

  private final AuctionRepository auctions;
  private final Clock clock;

  AuctionBidding(AuctionRepository auctions, Clock clock) {
    this.auctions = auctions;
    this.clock = clock;
  }

  @Transactional
  public OpenAuction lockOpenAuction(UUID auctionId) {
    Auction auction =
        auctions.findByIdForUpdate(auctionId).orElseThrow(() -> new BidUnavailable(auctionId));
    Instant now = Instant.now(clock);
    if (auction.state() != Auction.State.LIVE
        || now.isBefore(auction.startsAt())
        || !now.isBefore(auction.effectiveEndAt())) {
      throw new BidUnavailable(auctionId);
    }
    return new OpenAuction(
        auction.sellerId(),
        auction.currentAmountCents(),
        auction.minimumIncrementCents(),
        auction.effectiveEndAt());
  }

  public record OpenAuction(
      UUID sellerId, long currentAmountCents, long minimumIncrementCents, Instant effectiveEndAt) {}

  public static final class BidUnavailable extends RuntimeException {
    BidUnavailable(UUID auctionId) {
      super("Auction " + auctionId + " is not accepting bids.");
    }
  }
}
