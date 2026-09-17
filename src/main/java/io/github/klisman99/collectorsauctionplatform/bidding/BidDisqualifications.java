package io.github.klisman99.collectorsauctionplatform.bidding;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionBidding;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountSuspension;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberate bidding interface that permanently disqualifies a suspended account's accepted bids.
 */
@Service
public class BidDisqualifications {

  private final AuctionBidding auctions;
  private final AcceptedBidRepository acceptedBids;
  private final BidDisqualificationRepository disqualifications;
  private final ApplicationEventPublisher events;

  BidDisqualifications(
      AuctionBidding auctions,
      AcceptedBidRepository acceptedBids,
      BidDisqualificationRepository disqualifications,
      ApplicationEventPublisher events) {
    this.auctions = auctions;
    this.acceptedBids = acceptedBids;
    this.disqualifications = disqualifications;
    this.events = events;
  }

  @Transactional
  public List<UUID> acceptedBidAuctionIds(UUID bidderId) {
    return acceptedBids.findDistinctAuctionIdsByBidderId(bidderId);
  }

  @Transactional
  public void disqualifyAcceptedBids(
      UUID bidderId,
      RegularAccountSuspension suspension,
      Map<UUID, AuctionBidding.BidDisqualificationTarget> targets) {
    Map<UUID, List<AcceptedBid>> bidsByAuction =
        acceptedBids.findAllByBidderIdOrderByAuctionIdAndSequence(bidderId).stream()
            .collect(
                Collectors.groupingBy(
                    AcceptedBid::auctionId, java.util.LinkedHashMap::new, Collectors.toList()));
    for (Map.Entry<UUID, List<AcceptedBid>> auctionBids : bidsByAuction.entrySet()) {
      UUID auctionId = auctionBids.getKey();
      AuctionBidding.BidDisqualificationTarget target = targets.get(auctionId);
      if (target == null) {
        throw new IllegalArgumentException("The auction was not locked for bid disqualification.");
      }
      if (!target.acceptsDisqualification()) {
        continue;
      }

      List<UUID> candidateBidIds = auctionBids.getValue().stream().map(AcceptedBid::id).toList();
      java.util.Set<UUID> alreadyDisqualified =
          disqualifications.findAllByAcceptedBidIdIn(candidateBidIds).stream()
              .map(BidDisqualification::acceptedBidId)
              .collect(Collectors.toSet());
      List<AcceptedBid> newlyDisqualified =
          auctionBids.getValue().stream()
              .filter(bid -> !alreadyDisqualified.contains(bid.id()))
              .toList();
      if (newlyDisqualified.isEmpty()) {
        continue;
      }

      List<BidDisqualification> persisted =
          newlyDisqualified.stream()
              .map(bid -> BidDisqualification.forAccountSuspension(bid, suspension))
              .toList();
      disqualifications.saveAll(persisted);

      List<AcceptedBid> eligibleBids =
          acceptedBids.findAllEligibleByAuctionIdOrderBySequenceDesc(auctionId);
      long currentAmountCents =
          eligibleBids.isEmpty()
              ? target.openingAmountCents()
              : eligibleBids.getFirst().amountCents();
      AuctionBidding.PublicProjection projection =
          auctions.recalculateEligibleBidProjection(
              auctionId, eligibleBids.size(), currentAmountCents);

      for (int index = 0; index < newlyDisqualified.size(); index++) {
        AcceptedBid bid = newlyDisqualified.get(index);
        BidDisqualification disqualification = persisted.get(index);
        events.publishEvent(
            new BidDisqualified(
                bid.id(),
                auctionId,
                bid.bidderId(),
                bid.sequence(),
                projection.version(),
                disqualification.actorId(),
                disqualification.reasonCategory(),
                disqualification.publicReason(),
                disqualification.internalNote(),
                disqualification.disqualifiedAt(),
                projection.currentAmountCents(),
                projection.nextMinimumAmountCents(),
                projection.reserveMet(),
                projection.effectiveEndAt()));
      }
    }
  }
}
