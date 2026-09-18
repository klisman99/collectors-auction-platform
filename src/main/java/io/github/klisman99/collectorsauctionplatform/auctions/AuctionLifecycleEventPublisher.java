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
            auction.projectionVersion(),
            null,
            null,
            publicReason,
            null,
            null,
            occurredAt,
            previousTerms,
            new AuctionLifecycleEvent.PublishedTerms(
                auction.reserveAmountCents(), auction.startsAt(), auction.endsAt()),
            null,
            null,
            null,
            null,
            auction.sellerDecisionDeadlineAt()));
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
            auction.projectionVersion(),
            actorId,
            reasonCategory,
            publicReason,
            internalNote,
            itemDisposition,
            occurredAt,
            null,
            new AuctionLifecycleEvent.PublishedTerms(
                auction.reserveAmountCents(), auction.startsAt(), auction.endsAt()),
            null,
            null,
            null,
            null,
            auction.sellerDecisionDeadlineAt()));
  }

  void publishOutcome(
      Auction auction,
      AuctionBidding.ClosingOutcome outcome,
      AuctionBidding.FinalBid finalBid,
      Instant occurredAt) {
    AccountDirectory.AccountContact seller = accounts.regularAccount(auction.sellerId());
    AccountDirectory.AccountContact winner =
        finalBid == null ? null : accounts.regularAccount(finalBid.bidderId());
    AuctionLifecycleEvent.Type type =
        switch (outcome) {
          case SOLD -> AuctionLifecycleEvent.Type.SOLD;
          case UNSOLD -> AuctionLifecycleEvent.Type.UNSOLD;
          case AWAITING_SELLER_DECISION -> AuctionLifecycleEvent.Type.AWAITING_SELLER_DECISION;
        };
    events.publishEvent(
        new AuctionLifecycleEvent(
            auction.id(),
            auction.itemId(),
            auction.sellerId(),
            seller.email(),
            auction.itemSnapshot().title(),
            type,
            auction.projectionVersion(),
            null,
            null,
            null,
            null,
            null,
            occurredAt,
            null,
            new AuctionLifecycleEvent.PublishedTerms(
                auction.reserveAmountCents(), auction.startsAt(), auction.endsAt()),
            finalBid == null ? null : finalBid.amountCents(),
            winner == null ? null : winner.accountId(),
            winner == null ? null : winner.publicHandle(),
            winner == null ? null : winner.email(),
            auction.sellerDecisionDeadlineAt()));
  }

  void publishSellerDecision(
      Auction auction,
      AuctionLifecycleEvent.Type type,
      AuctionBidding.FinalBid finalBid,
      Instant occurredAt) {
    AccountDirectory.AccountContact seller = accounts.regularAccount(auction.sellerId());
    AccountDirectory.AccountContact bidder =
        finalBid == null ? null : accounts.regularAccount(finalBid.bidderId());
    events.publishEvent(
        new AuctionLifecycleEvent(
            auction.id(),
            auction.itemId(),
            auction.sellerId(),
            seller.email(),
            auction.itemSnapshot().title(),
            type,
            auction.projectionVersion(),
            null,
            null,
            null,
            null,
            null,
            occurredAt,
            null,
            new AuctionLifecycleEvent.PublishedTerms(
                auction.reserveAmountCents(), auction.startsAt(), auction.endsAt()),
            finalBid == null ? null : finalBid.amountCents(),
            bidder == null ? null : bidder.accountId(),
            bidder == null ? null : bidder.publicHandle(),
            bidder == null ? null : bidder.email(),
            auction.sellerDecisionDeadlineAt()));
  }
}
