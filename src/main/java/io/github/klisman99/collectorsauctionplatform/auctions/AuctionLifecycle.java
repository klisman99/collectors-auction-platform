package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuctionLifecycle {

  private final AuctionRepository auctions;
  private final Clock clock;
  private final CatalogAuctioning catalog;
  private final AuctionLifecycleEventPublisher lifecycleEvents;
  private final ApplicationEventPublisher events;

  AuctionLifecycle(
      AuctionRepository auctions,
      Clock clock,
      CatalogAuctioning catalog,
      AuctionLifecycleEventPublisher lifecycleEvents,
      ApplicationEventPublisher events) {
    this.auctions = auctions;
    this.clock = clock;
    this.catalog = catalog;
    this.lifecycleEvents = lifecycleEvents;
    this.events = events;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(fixedDelayString = "${platform.auctions.reconciliation-delay-ms:1000}")
  @Transactional
  public void reconcileDueAuctions() {
    Instant now = Instant.now(clock);
    Set<Auction> due = new LinkedHashSet<>(auctions.findDueScheduledForUpdate(now));
    due.addAll(auctions.findDueLiveForUpdate(now));
    due.addAll(auctions.findClaimedClosingForUpdate());
    due.addAll(auctions.findDueSellerDecisionsForUpdate(now));
    for (Auction auction : due) {
      if (auction.sellerDecisionExpired(now)) {
        AuctionBidding.FinalBid finalBid =
            new AuctionBidding.FinalBid(
                auction.finalBidId(),
                auction.finalBidderId(),
                auction.finalBidderPseudonym(),
                auction.finalAmountCents());
        auction.expireSellerDecision(now);
        catalog.releaseUnchangedItem(auction.itemId(), now);
        lifecycleEvents.publishSellerDecision(
            auction, AuctionLifecycleEvent.Type.SELLER_DECISION_EXPIRED, finalBid, now);
      } else if (auction.isClosing()) {
        events.publishEvent(new AuctionClosingClaimed(auction.id(), now));
      } else if (auction.claimClosing(now)) {
        events.publishEvent(new AuctionClosingClaimed(auction.id(), now));
      } else {
        auction.start(now);
        lifecycleEvents.publish(auction, AuctionLifecycleEvent.Type.STARTED, null, now, null);
      }
    }
  }
}
