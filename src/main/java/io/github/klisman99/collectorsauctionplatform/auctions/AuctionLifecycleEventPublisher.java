package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import java.time.Instant;
import java.util.UUID;
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
            null,
            null,
            publicReason,
            null,
            null,
            occurredAt,
            previousTerms,
            new AuctionLifecycleEvent.PublishedTerms(
                auction.reserveAmountCents(), auction.startsAt(), auction.endsAt())));
  }

  void publishAdministrative(
      Auction auction,
      AuctionLifecycleEvent.Type type,
      UUID actorId,
      SuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote,
      AdministrativeItemDisposition itemDisposition,
      Instant occurredAt) {
    AccountDirectory.AccountContact contact = accounts.regularAccount(auction.sellerId());
    events.publishEvent(
        new AuctionLifecycleEvent(
            auction.id(),
            auction.itemId(),
            auction.sellerId(),
            contact.email(),
            auction.itemSnapshot().title(),
            type,
            actorId,
            reasonCategory,
            publicReason,
            internalNote,
            itemDisposition,
            occurredAt,
            null,
            new AuctionLifecycleEvent.PublishedTerms(
                auction.reserveAmountCents(), auction.startsAt(), auction.endsAt())));
  }
}
