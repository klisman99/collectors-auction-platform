package io.github.klisman99.collectorsauctionplatform.settlement;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionSold;
import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class AuctionSoldListener {

  private final SaleRepository sales;
  private final AccountDirectory accounts;
  private final SaleTransitionPublisher transitions;

  AuctionSoldListener(
      SaleRepository sales, AccountDirectory accounts, SaleTransitionPublisher transitions) {
    this.sales = sales;
    this.accounts = accounts;
    this.transitions = transitions;
  }

  @ApplicationModuleListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void createSale(AuctionSold event) {
    if (!sales.existsByAuctionId(event.auctionId())) {
      Sale sale =
          Sale.from(
              event,
              accounts.regularAccount(event.sellerId()).publicHandle(),
              accounts.regularAccount(event.buyerId()).publicHandle());
      sales.save(sale);
      transitions.publish(sale, SaleTransitionEvent.Type.CREATED, null, event.occurredAt());
    }
  }
}
