package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class AuctionLifecycleEventPublisher {

  private final AccountDirectory accounts;
  private final ApplicationEventPublisher events;

  AuctionLifecycleEventPublisher(AccountDirectory accounts, ApplicationEventPublisher events) {
    this.accounts = accounts;
    this.events = events;
  }

  void publish(
      Auction auction,
      AuctionLifecycleEvent.Type type,
      String publicReason,
      Instant occurredAt,
      AuctionLifecycleEvent.PublishedTerms previousTerms) {
    AccountDirectory.AccountContact contact = accounts.regularAccount(auction.sellerId());
    events.publishEvent(
        new AuctionLifecycleEvent(
            auction.id(),
            auction.itemId(),
            auction.sellerId(),
            contact.email(),
            auction.itemSnapshot().title(),
            type,
            publicReason,
            occurredAt,
            previousTerms,
            new AuctionLifecycleEvent.PublishedTerms(
                auction.reserveAmountCents(), auction.startsAt(), auction.endsAt())));
  }
}
