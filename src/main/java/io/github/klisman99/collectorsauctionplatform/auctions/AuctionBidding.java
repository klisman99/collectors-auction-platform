package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deliberate auctions-module gate used by bidding while its transaction is active. */
@Service
public class AuctionBidding {

  private final AuctionRepository auctions;
  private final CatalogAuctioning catalog;
  private final Clock clock;
  private final AuctionLifecycleEventPublisher lifecycleEvents;
  private final ApplicationEventPublisher events;

  AuctionBidding(
      AuctionRepository auctions,
      CatalogAuctioning catalog,
      Clock clock,
      AuctionLifecycleEventPublisher lifecycleEvents,
      ApplicationEventPublisher events) {
    this.auctions = auctions;
    this.catalog = catalog;
    this.clock = clock;
    this.lifecycleEvents = lifecycleEvents;
    this.events = events;
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

  public PublicProjection recordAcceptedBid(UUID auctionId, long amountCents, Instant acceptedAt) {
    Auction auction = auctions.findById(auctionId).orElseThrow(() -> new BidUnavailable(auctionId));
    auction.recordAcceptedBid(amountCents, acceptedAt);
    return PublicProjection.from(auction);
  }

  @Transactional
  public PublicProjection recalculateEligibleBidProjection(
      UUID auctionId, long eligibleBidCount, long currentAmountCents) {
    Auction auction = auctions.findById(auctionId).orElseThrow(() -> new BidUnavailable(auctionId));
    auction.recalculateEligibleBidProjection(eligibleBidCount, currentAmountCents);
    return PublicProjection.from(auction);
  }

  /** Recalculates public eligibility and safely replaces an awaiting below-reserve offer. */
  @Transactional
  public PublicProjection recalculateEligibleBidProjectionAfterDisqualification(
      UUID auctionId,
      long eligibleBidCount,
      long currentAmountCents,
      FinalBid highestEligibleBid,
      Instant recalculatedAt) {
    Auction auction =
        auctions.findByIdForUpdate(auctionId).orElseThrow(() -> new BidUnavailable(auctionId));
    Auction.DisqualificationResult result =
        auction.recalculateEligibleBidProjectionAfterDisqualification(
            eligibleBidCount, currentAmountCents, highestEligibleBid, recalculatedAt);
    if (result == Auction.DisqualificationResult.NO_ELIGIBLE_BID) {
      catalog.releaseUnchangedItem(auction.itemId(), recalculatedAt);
      lifecycleEvents.publishSellerDecision(
          auction,
          AuctionLifecycleEvent.Type.SELLER_DECISION_NO_ELIGIBLE_BID,
          null,
          recalculatedAt);
    } else if (result == Auction.DisqualificationResult.REOPENED) {
      lifecycleEvents.publishSellerDecision(
          auction,
          AuctionLifecycleEvent.Type.SELLER_DECISION_REOPENED,
          highestEligibleBid,
          recalculatedAt);
    }
    return PublicProjection.from(auction);
  }

  /** Locks a durably claimed auction so bidding can select its one eligible final bid. */
  @Transactional
  public ClosingAuction lockClaimedAuction(UUID auctionId) {
    Optional<Auction> result = auctions.findByIdForUpdate(auctionId);
    if (result.isEmpty() || !result.get().isClosing()) {
      return ClosingAuction.notClaimed();
    }
    Auction auction = result.get();
    return ClosingAuction.claimed(auction.reserveAmountCents());
  }

  /** Records an outcome while holding the same auction lock used for bid acceptance. */
  @Transactional
  public boolean recordClosingOutcome(
      UUID auctionId, ClosingOutcome outcome, FinalBid finalBid, Instant recordedAt) {
    Auction auction =
        auctions.findByIdForUpdate(auctionId).orElseThrow(() -> new BidUnavailable(auctionId));
    if (!auction.recordClosingOutcome(outcome, finalBid, recordedAt)) {
      return false;
    }
    if (outcome == ClosingOutcome.UNSOLD) {
      catalog.releaseUnchangedItem(auction.itemId(), recordedAt);
    }
    lifecycleEvents.publishOutcome(auction, outcome, finalBid, recordedAt);
    if (outcome == ClosingOutcome.SOLD) {
      events.publishEvent(
          new AuctionSold(
              auction.id(),
              auction.itemId(),
              auction.sellerId(),
              finalBid.bidderId(),
              finalBid.amountCents(),
              recordedAt));
    }
    return true;
  }

  public record OpenAuction(
      UUID sellerId,
      long currentAmountCents,
      long minimumIncrementCents,
      Instant effectiveEndAt,
      Instant acceptedAt) {}

  public record PublicProjection(
      long version,
      long currentAmountCents,
      long nextMinimumAmountCents,
      boolean reserveMet,
      Instant effectiveEndAt) {

    private static PublicProjection from(Auction auction) {
      return new PublicProjection(
          auction.projectionVersion(),
          auction.currentAmountCents(),
          auction.nextMinimumAmountCents(),
          auction.reserveMet(),
          auction.effectiveEndAt());
    }
  }

  public record BidDisqualificationTarget(
      Auction.State state, long openingAmountCents, boolean acceptsDisqualification) {}

  public record ClosingAuction(boolean claimed, Long reserveAmountCents) {

    static ClosingAuction notClaimed() {
      return new ClosingAuction(false, null);
    }

    static ClosingAuction claimed(Long reserveAmountCents) {
      return new ClosingAuction(true, reserveAmountCents);
    }
  }

  public record FinalBid(UUID bidId, UUID bidderId, String bidderPseudonym, long amountCents) {}

  public enum ClosingOutcome {
    SOLD,
    UNSOLD,
    AWAITING_SELLER_DECISION
  }

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
