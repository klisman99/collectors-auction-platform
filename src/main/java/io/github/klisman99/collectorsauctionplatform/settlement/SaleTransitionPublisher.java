package io.github.klisman99.collectorsauctionplatform.settlement;

import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SaleTransitionPublisher {

  private final AccountDirectory accounts;
  private final ApplicationEventPublisher events;

  SaleTransitionPublisher(AccountDirectory accounts, ApplicationEventPublisher events) {
    this.accounts = accounts;
    this.events = events;
  }

  void publish(Sale sale, SaleTransitionEvent.Type type, UUID actorId, Instant occurredAt) {
    AccountDirectory.AccountContact seller = accounts.regularAccount(sale.sellerId());
    AccountDirectory.AccountContact buyer = accounts.regularAccount(sale.buyerId());
    events.publishEvent(
        new SaleTransitionEvent(
            sale.id(),
            sale.auctionId(),
            sale.itemId(),
            sale.itemTitle(),
            sale.sellerId(),
            sale.sellerHandle(),
            seller.email(),
            sale.buyerId(),
            sale.buyerHandle(),
            buyer.email(),
            sale.amountCents(),
            sale.state().name(),
            type,
            actorId,
            occurredAt,
            sale.paymentDeadlineAt(),
            sale.shipmentDeadlineAt(),
            sale.carrier(),
            sale.trackingReference()));
  }
}
