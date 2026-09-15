package io.github.klisman99.collectorsauctionplatform.auctions;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
    BidAvailability availability = inspectBidWindow(auctionId);
    if (!availability.accepting()) {
      throw new BidUnavailable(auctionId);
    }
    return availability.auction();
  }

  @Transactional
  public BidAvailability inspectBidWindow(UUID auctionId) {
    Optional<Auction> result = auctions.findByIdForUpdate(auctionId);
    if (result.isEmpty()) {
      return BidAvailability.notFound();
    }
    Auction auction = result.get();
    Instant now = Instant.now(clock);
    if (auction.state() != Auction.State.LIVE
        || now.isBefore(auction.startsAt())
        || !now.isBefore(auction.effectiveEndAt())) {
      return BidAvailability.unavailable();
    }
    return BidAvailability.accepting(
        new OpenAuction(
            auction.sellerId(),
            auction.currentAmountCents(),
            auction.minimumIncrementCents(),
            auction.effectiveEndAt(),
            now));
  }

  public void recordAcceptedBid(UUID auctionId, long amountCents, Instant acceptedAt) {
    Auction auction = auctions.findById(auctionId).orElseThrow(() -> new BidUnavailable(auctionId));
    auction.recordAcceptedBid(amountCents, acceptedAt);
  }

  public record OpenAuction(
      UUID sellerId,
      long currentAmountCents,
      long minimumIncrementCents,
      Instant effectiveEndAt,
      Instant acceptedAt) {}

  public record BidAvailability(State state, OpenAuction auction) {

    public BidAvailability {
      if ((state == State.ACCEPTING) != (auction != null)) {
        throw new IllegalArgumentException("Only an accepting bid window contains auction data.");
      }
    }

    static BidAvailability notFound() {
      return new BidAvailability(State.NOT_FOUND, null);
    }

    static BidAvailability unavailable() {
      return new BidAvailability(State.UNAVAILABLE, null);
    }

    static BidAvailability accepting(OpenAuction auction) {
      return new BidAvailability(State.ACCEPTING, auction);
    }

    public boolean exists() {
      return state != State.NOT_FOUND;
    }

    public boolean accepting() {
      return state == State.ACCEPTING;
    }

    public enum State {
      NOT_FOUND,
      UNAVAILABLE,
      ACCEPTING
    }
  }

  public static final class BidUnavailable extends RuntimeException {
    BidUnavailable(UUID auctionId) {
      super("Auction " + auctionId + " is not accepting bids.");
    }
  }
}
