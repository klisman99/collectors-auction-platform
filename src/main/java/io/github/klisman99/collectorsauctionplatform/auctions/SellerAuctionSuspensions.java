package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountSuspension;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deliberate auctions interface that freezes every active auction for a suspended seller. */
@Service
public class SellerAuctionSuspensions {

  private final AuctionRepository auctions;
  private final AuctionLifecycleEventPublisher lifecycleEvents;

  SellerAuctionSuspensions(
      AuctionRepository auctions, AuctionLifecycleEventPublisher lifecycleEvents) {
    this.auctions = auctions;
    this.lifecycleEvents = lifecycleEvents;
  }

  @Transactional
  public Map<UUID, AuctionBidding.BidDisqualificationTarget> suspendScheduledAndLiveAuctions(
      UUID sellerId, List<UUID> bidAuctionIds, RegularAccountSuspension suspension) {
    Set<UUID> sellerAuctionIds = Set.copyOf(auctions.findSuspendableIdsBySellerId(sellerId));
    List<UUID> auctionIdsToLock =
        java.util.stream.Stream.concat(sellerAuctionIds.stream(), bidAuctionIds.stream())
            .distinct()
            .sorted(Comparator.comparing(UUID::toString))
            .toList();
    Map<UUID, Auction> lockedAuctions = new LinkedHashMap<>();
    for (UUID auctionId : auctionIdsToLock) {
      Auction auction = auctions.findByIdForUpdate(auctionId).orElseThrow();
      lockedAuctions.put(auctionId, auction);
    }
    for (UUID auctionId : sellerAuctionIds) {
      Auction auction = lockedAuctions.get(auctionId);
      if (auction.state() != Auction.State.SCHEDULED && auction.state() != Auction.State.LIVE) {
        continue;
      }
      auction.suspendForSellerAccount(
          suspension.publicReason(), suspension.internalNote(), suspension.occurredAt());
      lifecycleEvents.publishAdministrative(
          auction,
          AuctionLifecycleEvent.Type.SUSPENDED,
          suspension.actorId(),
          SuspensionReasonCategory.ACCOUNT_SUSPENSION,
          suspension.publicReason(),
          suspension.internalNote(),
          null,
          suspension.occurredAt());
    }
    Map<UUID, AuctionBidding.BidDisqualificationTarget> bidTargets = new LinkedHashMap<>();
    for (UUID auctionId : bidAuctionIds) {
      Auction auction = lockedAuctions.get(auctionId);
      bidTargets.put(
          auctionId,
          new AuctionBidding.BidDisqualificationTarget(
              auction.state(),
              auction.openingAmountCents(),
              auction.state() != Auction.State.SOLD
                  && auction.state() != Auction.State.UNSOLD
                  && auction.state() != Auction.State.CANCELLED));
    }
    return bidTargets;
  }
}
