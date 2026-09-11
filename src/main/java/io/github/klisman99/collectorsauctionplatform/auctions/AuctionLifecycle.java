package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuctionLifecycle {

  private final AuctionRepository auctions;
  private final CatalogAuctioning catalog;
  private final Clock clock;
  private final AuctionLifecycleEventPublisher lifecycleEvents;

  AuctionLifecycle(
      AuctionRepository auctions,
      CatalogAuctioning catalog,
      Clock clock,
      AuctionLifecycleEventPublisher lifecycleEvents) {
    this.auctions = auctions;
    this.catalog = catalog;
    this.clock = clock;
    this.lifecycleEvents = lifecycleEvents;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(fixedDelayString = "${platform.auctions.reconciliation-delay-ms:1000}")
  @Transactional
  public void reconcileDueAuctions() {
    Instant now = Instant.now(clock);
    Set<Auction> due = new LinkedHashSet<>(auctions.findDueScheduledForUpdate(now));
    due.addAll(auctions.findDueLiveForUpdate(now));
    for (Auction auction : due) {
      if (auction.isDueToEnd(now)) {
        auction.endWithoutBids();
        catalog.releaseUnchangedItem(auction.itemId(), now);
        lifecycleEvents.publish(auction, AuctionLifecycleEvent.Type.ENDED, null, now, null);
      } else {
        auction.start(now);
        lifecycleEvents.publish(auction, AuctionLifecycleEvent.Type.STARTED, null, now, null);
      }
    }
  }
}
