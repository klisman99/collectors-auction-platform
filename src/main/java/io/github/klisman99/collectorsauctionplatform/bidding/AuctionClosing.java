package io.github.klisman99.collectorsauctionplatform.bidding;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionBidding;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Selects the highest durable eligible bid once an auction has stopped accepting bids. */
@Service
public class AuctionClosing {

  private final AuctionBidding auctions;
  private final AcceptedBidRepository acceptedBids;
  private final Clock clock;

  AuctionClosing(AuctionBidding auctions, AcceptedBidRepository acceptedBids, Clock clock) {
    this.auctions = auctions;
    this.acceptedBids = acceptedBids;
    this.clock = clock;
  }

  @Transactional
  public void closeClaim(UUID auctionId) {
    AuctionBidding.ClosingAuction auction = auctions.lockClaimedAuction(auctionId);
    if (!auction.claimed()) {
      return;
    }

    AcceptedBid eligibleBid =
        acceptedBids.findAllEligibleByAuctionIdOrderBySequenceDesc(auctionId).stream()
            .findFirst()
            .orElse(null);
    AuctionBidding.FinalBid finalBid =
        eligibleBid == null
            ? null
            : new AuctionBidding.FinalBid(
                eligibleBid.id(),
                eligibleBid.bidderId(),
                eligibleBid.bidderPseudonym(),
                eligibleBid.amountCents());
    auctions.recordClosingOutcome(
        auctionId, outcome(auction.reserveAmountCents(), finalBid), finalBid, Instant.now(clock));
  }

  private AuctionBidding.ClosingOutcome outcome(
      Long reserveAmountCents, AuctionBidding.FinalBid finalBid) {
    if (finalBid == null) {
      return AuctionBidding.ClosingOutcome.UNSOLD;
    }
    if (reserveAmountCents == null || finalBid.amountCents() >= reserveAmountCents) {
      return AuctionBidding.ClosingOutcome.SOLD;
    }
    return AuctionBidding.ClosingOutcome.AWAITING_SELLER_DECISION;
  }
}
