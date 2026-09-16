package io.github.klisman99.collectorsauctionplatform.settlement;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionSold;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class AuctionSoldListener {

  private final SaleRepository sales;

  AuctionSoldListener(SaleRepository sales) {
    this.sales = sales;
  }

  @ApplicationModuleListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void createSale(AuctionSold event) {
    if (!sales.existsByAuctionId(event.auctionId())) {
      sales.save(Sale.from(event));
    }
  }
}
