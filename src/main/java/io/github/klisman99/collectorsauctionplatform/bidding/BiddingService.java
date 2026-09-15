package io.github.klisman99.collectorsauctionplatform.bidding;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionBidding;
import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BiddingService {

  private final AuctionBidding auctions;
  private final AccountDirectory accounts;
  private final AcceptedBidRepository acceptedBids;
  private final BidAttemptRepository attempts;
  private final BidderPseudonymRepository pseudonyms;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  BiddingService(
      AuctionBidding auctions,
      AccountDirectory accounts,
      AcceptedBidRepository acceptedBids,
      BidAttemptRepository attempts,
      BidderPseudonymRepository pseudonyms,
      Clock clock,
      ApplicationEventPublisher events) {
    this.auctions = auctions;
    this.accounts = accounts;
    this.acceptedBids = acceptedBids;
    this.attempts = attempts;
    this.pseudonyms = pseudonyms;
    this.clock = clock;
    this.events = events;
  }

  @Transactional
  BidCommandResult place(UUID bidderId, UUID auctionId, UUID idempotencyKey, long amountCents) {
    Instant receivedAt = Instant.now(clock);
    AuctionBidding.BidAvailability availability = auctions.inspectBidWindow(auctionId);
    var previous =
        attempts
            .findAllByAuctionIdAndBidderIdAndIdempotencyKeyOrderByReceivedAtAsc(
                auctionId, bidderId, idempotencyKey)
            .stream()
            .filter(BidAttempt::isOriginalResult)
            .findFirst();
    if (previous.isPresent()) {
      if (previous.get().amountCents() == amountCents) {
        return recordReplay(
            auctionId,
            bidderId,
            idempotencyKey,
            amountCents,
            previous.get().replayedResult(),
            receivedAt);
      }
      return record(
          auctionId,
          bidderId,
          idempotencyKey,
          amountCents,
          new BidCommandResult(
              BidCommandResult.Status.IDEMPOTENCY_CONFLICT,
              "BID_IDEMPOTENCY_CONFLICT",
              amountCents,
              null,
              null,
              null,
              null),
          receivedAt);
    }
    if (!availability.exists()) {
      return record(
          auctionId,
          bidderId,
          idempotencyKey,
          amountCents,
          new BidCommandResult(
              BidCommandResult.Status.REJECTED,
              "BID_AUCTION_NOT_FOUND",
              amountCents,
              null,
              null,
              null,
              null),
          receivedAt);
    }

    if (!availability.accepting()) {
      return record(
          auctionId,
          bidderId,
          idempotencyKey,
          amountCents,
          new BidCommandResult(
              BidCommandResult.Status.REJECTED,
              "BID_LATE_OR_UNAVAILABLE",
              amountCents,
              null,
              null,
              null,
              null),
          receivedAt);
    }
    AuctionBidding.OpenAuction auction = availability.auction();

    AccountDirectory.TradingAccount bidder = accounts.tradingAccount(bidderId);
    if (auction.sellerId().equals(bidderId)) {
      return record(
          auctionId,
          bidderId,
          idempotencyKey,
          amountCents,
          rejected(amountCents, "BIDDER_INELIGIBLE", null),
          receivedAt);
    }
    if (!bidder.verified()) {
      return record(
          auctionId,
          bidderId,
          idempotencyKey,
          amountCents,
          rejected(amountCents, "BIDDER_UNVERIFIED", null),
          receivedAt);
    }
    if (!bidder.active()) {
      return record(
          auctionId,
          bidderId,
          idempotencyKey,
          amountCents,
          rejected(amountCents, "BIDDER_INACTIVE", null),
          receivedAt);
    }

    if (attempts.countByAuctionIdAndBidderIdAndReceivedAtGreaterThanEqual(
            auctionId, bidderId, receivedAt.minusSeconds(1))
        >= 10) {
      return record(
          auctionId,
          bidderId,
          idempotencyKey,
          amountCents,
          new BidCommandResult(
              BidCommandResult.Status.RATE_LIMITED,
              "BID_RATE_LIMITED",
              amountCents,
              null,
              null,
              null,
              null),
          receivedAt);
    }

    var latest = acceptedBids.findFirstByAuctionIdOrderBySequenceDesc(auctionId);
    long requiredAmount =
        latest.isEmpty()
            ? auction.currentAmountCents()
            : Math.min(
                100_000_001L,
                Math.addExact(latest.get().amountCents(), auction.minimumIncrementCents()));
    if (amountCents < requiredAmount || amountCents > 100_000_000L) {
      return record(
          auctionId,
          bidderId,
          idempotencyKey,
          amountCents,
          rejected(amountCents, "BID_AMOUNT_TOO_LOW", requiredAmount),
          receivedAt);
    }

    long sequence = latest.map(bid -> bid.sequence() + 1).orElse(1L);
    BidderPseudonym pseudonym =
        pseudonyms
            .findByAuctionIdAndBidderId(auctionId, bidderId)
            .orElseGet(() -> pseudonyms.save(new BidderPseudonym(auctionId, bidderId)));
    Instant acceptedAt = auction.acceptedAt().truncatedTo(ChronoUnit.MICROS);
    AcceptedBid accepted =
        acceptedBids.save(
            new AcceptedBid(
                auctionId, bidderId, amountCents, sequence, acceptedAt, pseudonym.pseudonym()));
    auctions.recordAcceptedBid(auctionId, amountCents, acceptedAt);
    events.publishEvent(
        new BidAccepted(
            auctionId,
            bidderId,
            amountCents,
            sequence,
            pseudonym.pseudonym(),
            accepted.acceptedAt()));
    BidCommandResult result =
        new BidCommandResult(
            BidCommandResult.Status.ACCEPTED,
            "BID_ACCEPTED",
            amountCents,
            requiredAmount,
            accepted.sequence(),
            accepted.bidderPseudonym(),
            accepted.acceptedAt());
    return record(auctionId, bidderId, idempotencyKey, amountCents, result, receivedAt);
  }

  @Transactional(readOnly = true)
  List<PublicBid> history(UUID auctionId) {
    return acceptedBids.findAllByAuctionIdOrderBySequenceDesc(auctionId).stream()
        .map(
            bid ->
                new PublicBid(
                    bid.amountCents(), bid.acceptedAt(), bid.sequence(), bid.bidderPseudonym()))
        .toList();
  }

  private BidCommandResult rejected(long amountCents, String code, Long requiredAmountCents) {
    return new BidCommandResult(
        BidCommandResult.Status.REJECTED, code, amountCents, requiredAmountCents, null, null, null);
  }

  private BidCommandResult record(
      UUID auctionId,
      UUID bidderId,
      UUID idempotencyKey,
      long amountCents,
      BidCommandResult result,
      Instant receivedAt) {
    attempts.save(
        new BidAttempt(auctionId, bidderId, idempotencyKey, amountCents, result, receivedAt));
    return result;
  }

  private BidCommandResult recordReplay(
      UUID auctionId,
      UUID bidderId,
      UUID idempotencyKey,
      long amountCents,
      BidCommandResult result,
      Instant receivedAt) {
    attempts.save(
        new BidAttempt(
            auctionId,
            bidderId,
            idempotencyKey,
            amountCents,
            BidCommandResult.Status.DEDUPLICATED,
            result,
            receivedAt));
    return result;
  }

  record PublicBid(long amountCents, Instant acceptedAt, long sequence, String bidderPseudonym) {}
}
