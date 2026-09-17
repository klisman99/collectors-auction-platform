package io.github.klisman99.collectorsauctionplatform.bidding;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionClosingClaimed;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class AuctionClosingClaimedListener {

  private final AuctionClosing closing;

  AuctionClosingClaimedListener(AuctionClosing closing) {
    this.closing = closing;
  }

  @ApplicationModuleListener
  void closeClaimedAuction(AuctionClosingClaimed event) {
    closing.closeClaim(event.auctionId());
  }
}
