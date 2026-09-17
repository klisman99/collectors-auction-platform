package io.github.klisman99.collectorsauctionplatform.bidding;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionBidding;
import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BiddingService {

  private final AuctionBidding auctions;
  private final AccountDirectory accounts;
  private final AcceptedBidRepository acceptedBids;
  private final BidDisqualificationRepository disqualifications;
  private final BidAttemptRepository attempts;
  private final BidderPseudonymRepository pseudonyms;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  BiddingService(
      AuctionBidding auctions,
      AccountDirectory accounts,
      AcceptedBidRepository acceptedBids,
      BidDisqualificationRepository disqualifications,
      BidAttemptRepository attempts,
      BidderPseudonymRepository pseudonyms,
      Clock clock,
      ApplicationEventPublisher events) {
    this.auctions = auctions;
    this.accounts = accounts;
    this.acceptedBids = acceptedBids;
    this.disqualifications = disqualifications;
    this.attempts = attempts;
    this.pseudonyms = pseudonyms;
    this.clock = clock;
    this.events = events;
  }

  @Transactional
  BidCommandResult place(UUID bidderId, UUID auctionId, UUID idempotencyKey, long amountCents) {
    Instant receivedAt = Instant.now(clock);
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

    AccountDirectory.TradingAccount bidder = accounts.lockTradingAccount(bidderId);
    AuctionBidding.BidAvailability availability = auctions.inspectBidWindow(auctionId);
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

    var latestEligible =
        acceptedBids.findAllEligibleByAuctionIdOrderBySequenceDesc(auctionId).stream().findFirst();
    long requiredAmount =
        latestEligible.isEmpty()
            ? auction.currentAmountCents()
            : Math.min(
                100_000_001L,
                Math.addExact(latestEligible.get().amountCents(), auction.minimumIncrementCents()));
    if (amountCents < requiredAmount || amountCents > 100_000_000L) {
      return record(
          auctionId,
          bidderId,
          idempotencyKey,
          amountCents,
          rejected(amountCents, "BID_AMOUNT_TOO_LOW", requiredAmount),
          receivedAt);
    }

    long sequence =
        acceptedBids
            .findFirstByAuctionIdOrderBySequenceDesc(auctionId)
            .map(bid -> bid.sequence() + 1)
            .orElse(1L);
    BidderPseudonym pseudonym =
        pseudonyms
            .findByAuctionIdAndBidderId(auctionId, bidderId)
            .orElseGet(() -> pseudonyms.save(new BidderPseudonym(auctionId, bidderId)));
    Instant acceptedAt = auction.acceptedAt().truncatedTo(ChronoUnit.MICROS);
    AcceptedBid accepted =
        acceptedBids.save(
            new AcceptedBid(
                auctionId, bidderId, amountCents, sequence, acceptedAt, pseudonym.pseudonym()));
    AuctionBidding.PublicProjection projection =
        auctions.recordAcceptedBid(auctionId, amountCents, acceptedAt);
    events.publishEvent(
        new BidAccepted(
            auctionId,
            bidderId,
            amountCents,
            sequence,
            projection.version(),
            pseudonym.pseudonym(),
            accepted.acceptedAt(),
            projection.currentAmountCents(),
            projection.nextMinimumAmountCents(),
            projection.reserveMet(),
            projection.effectiveEndAt()));
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
    List<AcceptedBid> bids = acceptedBids.findAllByAuctionIdOrderBySequenceDesc(auctionId);
    Map<UUID, BidDisqualification> disqualificationsByBidId =
        disqualifications
            .findAllByAcceptedBidIdIn(bids.stream().map(AcceptedBid::id).toList())
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    BidDisqualification::acceptedBidId, value -> value));
    return bids.stream()
        .map(
            bid ->
                new PublicBid(
                    bid.amountCents(),
                    bid.acceptedAt(),
                    bid.sequence(),
                    bid.bidderPseudonym(),
                    disqualificationsByBidId.containsKey(bid.id())))
        .toList();
  }

  @Transactional(readOnly = true)
  List<OperationalBid> operationalHistory(UUID auctionId, boolean includeInternalNotes) {
    List<AcceptedBid> bids = acceptedBids.findAllByAuctionIdOrderBySequenceDesc(auctionId);
    Map<UUID, BidDisqualification> disqualificationsByBidId =
        disqualifications
            .findAllByAcceptedBidIdIn(bids.stream().map(AcceptedBid::id).toList())
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    BidDisqualification::acceptedBidId, value -> value));
    return bids.stream()
        .map(
            bid -> {
              AccountDirectory.AccountContact bidder = accounts.regularAccount(bid.bidderId());
              BidDisqualification disqualification = disqualificationsByBidId.get(bid.id());
              return new OperationalBid(
                  bid.id(),
                  bid.amountCents(),
                  bid.acceptedAt(),
                  bid.sequence(),
                  bid.bidderPseudonym(),
                  bidder.accountId(),
                  bidder.publicHandle(),
                  disqualification != null,
                  disqualification == null ? null : disqualification.reasonCategory(),
                  disqualification == null ? null : disqualification.publicReason(),
                  disqualification == null || !includeInternalNotes
                      ? null
                      : disqualification.internalNote());
            })
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

  record PublicBid(
      long amountCents,
      Instant acceptedAt,
      long sequence,
      String bidderPseudonym,
      boolean disqualified) {}

  record OperationalBid(
      UUID id,
      long amountCents,
      Instant acceptedAt,
      long sequence,
      String bidderPseudonym,
      UUID bidderId,
      String bidderHandle,
      boolean disqualified,
      String reasonCategory,
      String publicReason,
      String internalNote) {}
}
