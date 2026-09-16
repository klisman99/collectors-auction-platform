package io.github.klisman99.collectorsauctionplatform.accountadministration;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionBidding;
import io.github.klisman99.collectorsauctionplatform.auctions.SellerAuctionSuspensions;
import io.github.klisman99.collectorsauctionplatform.bidding.BidDisqualifications;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountAdministration;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountReactivation;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountSuspension;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountSuspensionReasonCategory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates the single durable account-suspension transaction across owning business modules. */
@Service
public class AccountAdministrationService {

  private final RegularAccountAdministration accounts;
  private final SellerAuctionSuspensions auctions;
  private final BidDisqualifications bids;
  private final Clock clock;

  AccountAdministrationService(
      RegularAccountAdministration accounts,
      SellerAuctionSuspensions auctions,
      BidDisqualifications bids,
      Clock clock) {
    this.accounts = accounts;
    this.auctions = auctions;
    this.bids = bids;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<RegularAccountAdministration.RegularAccountView> list() {
    return accounts.list();
  }

  @Transactional
  public RegularAccountAdministration.RegularAccountView suspend(
      UUID actorId,
      UUID accountId,
      RegularAccountSuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote) {
    Instant now = Instant.now(clock);
    RegularAccountSuspension suspension =
        new RegularAccountSuspension(actorId, reasonCategory, publicReason, internalNote, now);
    RegularAccountAdministration.RegularAccountView account =
        accounts.suspend(accountId, suspension);
    List<UUID> bidAuctionIds = bids.acceptedBidAuctionIds(accountId);
    Map<UUID, AuctionBidding.BidDisqualificationTarget> bidTargets =
        auctions.suspendScheduledAndLiveAuctions(accountId, bidAuctionIds, suspension);
    bids.disqualifyAcceptedBids(accountId, suspension, bidTargets);
    return account;
  }

  @Transactional
  public RegularAccountAdministration.RegularAccountView reactivate(
      UUID actorId,
      UUID accountId,
      RegularAccountSuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote) {
    return accounts.reactivate(
        accountId,
        new RegularAccountReactivation(
            actorId, reasonCategory, publicReason, internalNote, Instant.now(clock)));
  }
}
