package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuctionService {

  private final AuctionRepository auctions;
  private final CatalogAuctioning catalog;
  private final AccountDirectory accounts;
  private final Clock clock;

  AuctionService(
      AuctionRepository auctions,
      CatalogAuctioning catalog,
      AccountDirectory accounts,
      Clock clock) {
    this.auctions = auctions;
    this.catalog = catalog;
    this.accounts = accounts;
    this.clock = clock;
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

    AccountDirectory.TradingAccount seller = accounts.tradingAccount(sellerId);
    if (!seller.eligible()) {
      throw AuctionApiException.accountIneligible();
    }

    try {
      CatalogAuctioning.ItemSnapshot item =
          catalog.lockApprovedItem(sellerId, command.itemId(), now);
      return auctions.save(
          Auction.schedule(sellerId, seller.publicHandle(), item, command, policy, now));
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
  List<Auction> scheduled() {
    return auctions.findAllByStateOrderByStartsAtAsc(Auction.State.SCHEDULED);
  }

  @Transactional(readOnly = true)
  AuctionItemSnapshotMedia image(UUID auctionId, UUID mediaId) {
    Auction auction = get(auctionId);
    return auction.snapshotMedia().stream()
        .filter(media -> media.mediaId().equals(mediaId))
        .findFirst()
        .orElseThrow(AuctionApiException::notFound);
  }

  @Transactional
  Auction updateEditableTerms(
      UUID sellerId, UUID auctionId, Long reserveAmountCents, Instant startsAt, Instant endsAt) {
    AccountDirectory.TradingAccount seller = accounts.tradingAccount(sellerId);
    if (!seller.eligible()) {
      throw AuctionApiException.accountIneligible();
    }
    Auction auction =
        auctions
            .findByIdAndSellerId(auctionId, sellerId)
            .orElseThrow(AuctionApiException::notFound);
    auction.updateEditableTerms(reserveAmountCents, startsAt, endsAt, Instant.now(clock));
    return auction;
  }

  boolean maySeeExactReserve(Auction auction, UUID viewerId, boolean administrator) {
    return administrator || (viewerId != null && auction.sellerId().equals(viewerId));
  }
}
