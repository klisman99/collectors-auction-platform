package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuctionService {

  private final AuctionRepository auctions;
  private final CatalogAuctioning catalog;
  private final AccountDirectory accounts;
  private final Clock clock;
  private final AuctionLifecycleEventPublisher lifecycleEvents;

  AuctionService(
      AuctionRepository auctions,
      CatalogAuctioning catalog,
      AccountDirectory accounts,
      Clock clock,
      AuctionLifecycleEventPublisher lifecycleEvents) {
    this.auctions = auctions;
    this.catalog = catalog;
    this.accounts = accounts;
    this.clock = clock;
    this.lifecycleEvents = lifecycleEvents;
  }

  @Transactional
  Auction schedule(UUID sellerId, ScheduleAuction command) {
    Instant now = Instant.now(clock);
    AuctionPolicySnapshot policy = AuctionPolicySnapshot.current();
    policy.validateTerms(
        command.openingAmountCents(),
        command.minimumIncrementCents(),
        command.reserveAmountCents(),
        command.startsAt(),
        command.endsAt(),
        now);

    AccountDirectory.TradingAccount seller = accounts.lockTradingAccount(sellerId);
    if (!seller.eligible()) {
      throw AuctionApiException.accountIneligible();
    }

    try {
      CatalogAuctioning.ItemSnapshot item =
          catalog.lockApprovedItem(sellerId, command.itemId(), now);
      Auction auction =
          auctions.save(
              Auction.schedule(sellerId, seller.publicHandle(), item, command, policy, now));
      lifecycleEvents.publish(auction, AuctionLifecycleEvent.Type.SCHEDULED, null, now, null);
      return auction;
    } catch (CatalogAuctioning.ItemNotAuctionable exception) {
      throw switch (exception.reason()) {
        case NOT_FOUND -> AuctionApiException.itemNotFound();
        case NOT_APPROVED -> AuctionApiException.itemNotApproved();
        case LOCKED -> AuctionApiException.itemAlreadyScheduled();
      };
    }
  }

  @Transactional(readOnly = true)
  Auction get(UUID auctionId) {
    return auctions.findById(auctionId).orElseThrow(AuctionApiException::notFound);
  }

  @Transactional(readOnly = true)
  AuctionAccess getVisible(UUID auctionId, UUID viewerId, boolean administrator) {
    return withAccess(get(auctionId), viewerId, administrator);
  }

  @Transactional(readOnly = true)
  Page<AuctionAccess> discover(
      AuctionDiscoveryView view, int page, int size, UUID viewerId, boolean administrator) {
    Page<Auction> result =
        switch (view) {
          case SCHEDULED ->
              auctions.findScheduledDiscovery(
                  PageRequest.of(page, size, Sort.by("startsAt").ascending()));
          case LIVE ->
              auctions.findLiveDiscovery(PageRequest.of(page, size, Sort.by("endsAt").ascending()));
          case ENDED ->
              auctions.findAllByStateIn(
                  List.of(Auction.State.SOLD, Auction.State.UNSOLD, Auction.State.CANCELLED),
                  PageRequest.of(page, size, Sort.by("endedAt").descending()));
        };
    return result.map(auction -> withAccess(auction, viewerId, administrator));
  }

  @Transactional(readOnly = true)
  AuctionItemSnapshotMedia image(UUID auctionId, UUID mediaId) {
    Auction auction = get(auctionId);
    return auction.snapshotMedia().stream()
        .filter(media -> media.mediaId().equals(mediaId))
        .findFirst()
        .orElseThrow(AuctionApiException::notFound);
  }

  @Transactional(readOnly = true)
  List<Auction> scheduledForSeller(UUID sellerId) {
    return auctions.findAllBySellerIdAndStateInOrderByStartsAtAsc(
        sellerId, List.of(Auction.State.DRAFT, Auction.State.SCHEDULED));
  }

  @Transactional(readOnly = true)
  List<Auction> suspendedQueue() {
    return auctions.findAllByStateOrderBySuspendedAtAsc(Auction.State.SUSPENDED);
  }

  @Transactional
  Auction suspend(
      AuctionOperator operator,
      UUID auctionId,
      SuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote) {
    Auction auction = lock(auctionId);
    Instant now = Instant.now(clock);
    auction.suspend(reasonCategory, publicReason, internalNote, now);
    lifecycleEvents.publishAdministrative(
        auction,
        AuctionLifecycleEvent.Type.SUSPENDED,
        operator.accountId(),
        reasonCategory,
        publicReason.trim(),
        internalNote,
        null,
        now);
    return auction;
  }

  @Transactional
  Auction release(AuctionOperator operator, UUID auctionId) {
    requireAdministrator(operator);
    Auction auction = lock(auctionId);
    Instant now = Instant.now(clock);
    auction.release(now);
    catalog.releaseUnchangedItem(auction.itemId(), now);
    lifecycleEvents.publishAdministrative(
        auction,
        AuctionLifecycleEvent.Type.RELEASED,
        operator.accountId(),
        null,
        null,
        null,
        null,
        now);
    return auction;
  }

  @Transactional
  Auction resume(AuctionOperator operator, UUID auctionId) {
    requireAdministrator(operator);
    Auction auction = lock(auctionId);
    Instant now = Instant.now(clock);
    auction.resume(now);
    lifecycleEvents.publishAdministrative(
        auction,
        AuctionLifecycleEvent.Type.RESUMED,
        operator.accountId(),
        null,
        null,
        null,
        null,
        now);
    return auction;
  }

  @Transactional
  Auction administrativelyCancel(
      AuctionOperator operator,
      UUID auctionId,
      SuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote,
      AdministrativeItemDisposition disposition) {
    requireAdministrator(operator);
    Auction auction = lock(auctionId);
    Instant now = Instant.now(clock);
    auction.administrativelyCancel(reasonCategory, publicReason, internalNote, disposition, now);
    switch (disposition) {
      case RELEASE_APPROVED_ITEM ->
          catalog.releaseApprovedItemAfterAdministrativeCancellation(auction.itemId(), now);
      case REVOKE_APPROVAL_TO_DRAFT ->
          catalog.revokeApprovalAfterAdministrativeCancellation(auction.itemId(), now);
    }
    lifecycleEvents.publishAdministrative(
        auction,
        AuctionLifecycleEvent.Type.ADMINISTRATIVELY_CANCELLED,
        operator.accountId(),
        reasonCategory,
        publicReason.trim(),
        internalNote,
        disposition,
        now);
    return auction;
  }

  private Auction lock(UUID auctionId) {
    return auctions.findByIdForUpdate(auctionId).orElseThrow(AuctionApiException::notFound);
  }

  private void requireAdministrator(AuctionOperator operator) {
    if (!operator.isAdministrator()) {
      throw AuctionApiException.administratorRequired();
    }
  }

  @Transactional
  Auction updateEditableTerms(
      UUID sellerId, UUID auctionId, Long reserveAmountCents, Instant startsAt, Instant endsAt) {
    AccountDirectory.TradingAccount seller = accounts.lockTradingAccount(sellerId);
    if (!seller.eligible()) {
      throw AuctionApiException.accountIneligible();
    }
    Auction auction =
        auctions
            .findByIdAndSellerId(auctionId, sellerId)
            .orElseThrow(AuctionApiException::notFound);
    AuctionLifecycleEvent.PublishedTerms previousTerms =
        new AuctionLifecycleEvent.PublishedTerms(
            auction.reserveAmountCents(), auction.startsAt(), auction.endsAt());
    Instant now = Instant.now(clock);
    if (auction.state() == Auction.State.DRAFT) {
      try {
        catalog.lockApprovedItem(sellerId, auction.itemId(), now);
      } catch (CatalogAuctioning.ItemNotAuctionable exception) {
        throw switch (exception.reason()) {
          case NOT_FOUND -> AuctionApiException.itemNotFound();
          case NOT_APPROVED -> AuctionApiException.itemNotApproved();
          case LOCKED -> AuctionApiException.itemAlreadyScheduled();
        };
      }
    }
    auction.updateEditableTerms(reserveAmountCents, startsAt, endsAt, now);
    lifecycleEvents.publish(
        auction, AuctionLifecycleEvent.Type.RESCHEDULED, null, now, previousTerms);
    return auction;
  }

  @Transactional
  Auction cancel(UUID sellerId, UUID auctionId, String publicReason) {
    AccountDirectory.TradingAccount seller = accounts.lockTradingAccount(sellerId);
    if (!seller.eligible()) {
      throw AuctionApiException.accountIneligible();
    }
    Auction auction =
        auctions
            .findByIdAndSellerId(auctionId, sellerId)
            .orElseThrow(AuctionApiException::notFound);
    Instant now = Instant.now(clock);
    auction.cancel(publicReason, now);
    catalog.releaseUnchangedItem(auction.itemId(), now);
    lifecycleEvents.publish(
        auction, AuctionLifecycleEvent.Type.CANCELLED, auction.cancellationReason(), now, null);
    return auction;
  }

  private AuctionAccess withAccess(Auction auction, UUID viewerId, boolean administrator) {
    return new AuctionAccess(
        auction,
        administrator || (viewerId != null && auction.sellerId().equals(viewerId)),
        administrator);
  }
}
