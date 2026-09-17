package io.github.klisman99.collectorsauctionplatform.auctions;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "auctions")
class Auction {

  @Id private UUID id;

  @Column(name = "item_id", nullable = false, updatable = false)
  private UUID itemId;

  @Column(name = "active_item_id")
  private UUID activeItemId;

  @Column(name = "seller_id", nullable = false, updatable = false)
  private UUID sellerId;

  @Column(name = "seller_handle", nullable = false, updatable = false)
  private String sellerHandle;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private State state;

  @Column(name = "opening_amount_cents", nullable = false, updatable = false)
  private long openingAmountCents;

  @Column(name = "minimum_increment_cents", nullable = false, updatable = false)
  private long minimumIncrementCents;

  @Column(name = "current_amount_cents", nullable = false)
  private long currentAmountCents;

  @Column(name = "accepted_bid_count", nullable = false)
  private long acceptedBidCount;

  @Column(name = "eligible_bid_count", nullable = false)
  private long eligibleBidCount;

  @Column(name = "reserve_amount_cents")
  private Long reserveAmountCents;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "scheduled_at", nullable = false, updatable = false)
  private Instant scheduledAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "cancellation_reason")
  private String cancellationReason;

  @Column(name = "final_bid_id")
  private UUID finalBidId;

  @Column(name = "final_bidder_id")
  private UUID finalBidderId;

  @Column(name = "final_bidder_pseudonym")
  private String finalBidderPseudonym;

  @Column(name = "final_amount_cents")
  private Long finalAmountCents;

  @Column(name = "final_outcome_recorded_at")
  private Instant finalOutcomeRecordedAt;

  @Column(name = "suspension_source_state")
  @Enumerated(EnumType.STRING)
  private State suspensionSourceState;

  @Column(name = "suspended_at")
  private Instant suspendedAt;

  @Column(name = "remaining_duration_millis")
  private Long remainingDurationMillis;

  @Embedded private AuctionItemSnapshot itemSnapshot;

  @Embedded private AuctionPolicySnapshot policySnapshot;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "auction_item_snapshot_media",
      joinColumns = @JoinColumn(name = "auction_id"))
  @OrderBy("sortOrder")
  private List<AuctionItemSnapshotMedia> snapshotMedia = new ArrayList<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "auction_timeline_events", joinColumns = @JoinColumn(name = "auction_id"))
  @OrderColumn(name = "sequence_number")
  private List<AuctionTimelineEntry> timeline = new ArrayList<>();

  protected Auction() {}

  static Auction schedule(
      UUID sellerId,
      String sellerHandle,
      CatalogAuctioning.ItemSnapshot source,
      ScheduleAuction command,
      AuctionPolicySnapshot policy,
      Instant now) {
    Auction auction = new Auction();
    auction.id = UUID.randomUUID();
    auction.itemId = source.itemId();
    auction.activeItemId = source.itemId();
    auction.sellerId = sellerId;
    auction.sellerHandle = sellerHandle;
    auction.state = State.SCHEDULED;
    auction.openingAmountCents = command.openingAmountCents();
    auction.minimumIncrementCents = command.minimumIncrementCents();
    auction.currentAmountCents = command.openingAmountCents();
    auction.acceptedBidCount = 0;
    auction.eligibleBidCount = 0;
    auction.reserveAmountCents = command.reserveAmountCents();
    auction.startsAt = command.startsAt();
    auction.endsAt = command.endsAt();
    auction.scheduledAt = now;
    auction.itemSnapshot = AuctionItemSnapshot.from(source);
    auction.policySnapshot = policy;
    auction.snapshotMedia = source.media().stream().map(AuctionItemSnapshotMedia::from).toList();
    auction.timeline.add(AuctionTimelineEntry.scheduled(now));
    return auction;
  }

  void updateEditableTerms(Long reserveAmountCents, Instant startsAt, Instant endsAt, Instant now) {
    boolean releasedDraft = state == State.DRAFT;
    if (!releasedDraft && (state != State.SCHEDULED || !now.isBefore(this.startsAt))) {
      throw AuctionApiException.notEditable();
    }
    if (reserveAmountCents != null
        && (this.reserveAmountCents == null || reserveAmountCents > this.reserveAmountCents)) {
      throw AuctionApiException.reserveCannotIncrease();
    }
    policySnapshot.validateTerms(
        openingAmountCents, minimumIncrementCents, reserveAmountCents, startsAt, endsAt, now);
    this.reserveAmountCents = reserveAmountCents;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    if (releasedDraft) {
      state = State.SCHEDULED;
      activeItemId = itemId;
    }
    this.timeline.add(AuctionTimelineEntry.rescheduled(now));
  }

  void cancel(String publicReason, Instant now) {
    if (state != State.SCHEDULED || !now.isBefore(startsAt)) {
      throw AuctionApiException.notCancellable();
    }
    if (publicReason == null || publicReason.isBlank() || publicReason.length() > 500) {
      throw AuctionApiException.invalidCancellationReason();
    }
    state = State.CANCELLED;
    activeItemId = null;
    endedAt = now;
    cancellationReason = publicReason.trim();
    timeline.add(AuctionTimelineEntry.cancelled(now, cancellationReason));
  }

  void start(Instant now) {
    if (state == State.SCHEDULED && !now.isBefore(startsAt) && now.isBefore(endsAt)) {
      state = State.LIVE;
      timeline.add(AuctionTimelineEntry.started(now));
    }
  }

  void suspend(
      SuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote,
      Instant now) {
    if ((state != State.SCHEDULED && state != State.LIVE)
        || (state == State.SCHEDULED && !now.isBefore(startsAt))
        || (state == State.LIVE && !now.isBefore(endsAt))) {
      throw AuctionApiException.notSuspendable();
    }
    applySuspension(reasonCategory, publicReason, internalNote, now);
  }

  void suspendForSellerAccount(String publicReason, String internalNote, Instant now) {
    if (state != State.SCHEDULED && state != State.LIVE) {
      throw AuctionApiException.notSuspendable();
    }
    applySuspension(SuspensionReasonCategory.ACCOUNT_SUSPENSION, publicReason, internalNote, now);
  }

  private void applySuspension(
      SuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote,
      Instant now) {
    validateAdministrativeReason(publicReason, internalNote);
    suspensionSourceState = state;
    suspendedAt = now;
    remainingDurationMillis =
        state == State.LIVE ? Math.max(0, Duration.between(now, endsAt).toMillis()) : null;
    state = State.SUSPENDED;
    timeline.add(
        AuctionTimelineEntry.suspended(now, reasonCategory, publicReason.trim(), internalNote));
  }

  void release(Instant now) {
    requireSuspendedFrom(State.SCHEDULED);
    state = State.DRAFT;
    activeItemId = null;
    clearSuspension();
    timeline.add(AuctionTimelineEntry.released(now));
  }

  void resume(Instant now) {
    requireSuspendedFrom(State.LIVE);
    endsAt = now.plusMillis(remainingDurationMillis);
    state = State.LIVE;
    clearSuspension();
    timeline.add(AuctionTimelineEntry.resumed(now));
  }

  void administrativelyCancel(
      SuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote,
      AdministrativeItemDisposition disposition,
      Instant now) {
    if (state != State.SUSPENDED) {
      throw AuctionApiException.suspensionResolutionInvalid();
    }
    validateAdministrativeReason(publicReason, internalNote);
    state = State.CANCELLED;
    activeItemId = null;
    endedAt = now;
    cancellationReason = publicReason.trim();
    clearSuspension();
    timeline.add(
        AuctionTimelineEntry.administrativelyCancelled(
            now, reasonCategory, cancellationReason, internalNote, disposition));
  }

  private void requireSuspendedFrom(State expectedSource) {
    if (state != State.SUSPENDED || suspensionSourceState != expectedSource) {
      throw AuctionApiException.suspensionResolutionInvalid();
    }
  }

  private void clearSuspension() {
    suspensionSourceState = null;
    suspendedAt = null;
    remainingDurationMillis = null;
  }

  private void validateAdministrativeReason(String publicReason, String internalNote) {
    if (publicReason == null || publicReason.isBlank() || publicReason.length() > 500) {
      throw AuctionApiException.invalidAdministrativeReason();
    }
    if (internalNote != null && internalNote.length() > 2000) {
      throw AuctionApiException.invalidAdministrativeReason();
    }
  }

  boolean isDueToEnd(Instant now) {
    return (state == State.SCHEDULED || state == State.LIVE) && !now.isBefore(endsAt);
  }

  boolean claimClosing(Instant now) {
    if (!isDueToEnd(now)) {
      return false;
    }
    state = State.CLOSING;
    return true;
  }

  boolean isClosing() {
    return state == State.CLOSING;
  }

  boolean recordClosingOutcome(
      AuctionBidding.ClosingOutcome outcome, AuctionBidding.FinalBid finalBid, Instant recordedAt) {
    if (state != State.CLOSING) {
      return false;
    }
    if (outcome == AuctionBidding.ClosingOutcome.UNSOLD && finalBid != null) {
      throw new IllegalArgumentException("An unsold auction cannot have a final bid.");
    }
    if (outcome != AuctionBidding.ClosingOutcome.UNSOLD && finalBid == null) {
      throw new IllegalArgumentException("A bid is required for this auction outcome.");
    }

    state =
        switch (outcome) {
          case SOLD -> State.SOLD;
          case UNSOLD -> State.UNSOLD;
          case AWAITING_SELLER_DECISION -> State.AWAITING_SELLER_DECISION;
        };
    if (finalBid != null) {
      finalBidId = finalBid.bidId();
      finalBidderId = finalBid.bidderId();
      finalBidderPseudonym = finalBid.bidderPseudonym();
      finalAmountCents = finalBid.amountCents();
      finalOutcomeRecordedAt = recordedAt;
    }
    if (state == State.SOLD || state == State.UNSOLD) {
      activeItemId = null;
      endedAt = endsAt;
      timeline.add(AuctionTimelineEntry.ended(endsAt));
    }
    return true;
  }

  UUID id() {
    return id;
  }

  UUID itemId() {
    return itemId;
  }

  UUID sellerId() {
    return sellerId;
  }

  String sellerHandle() {
    return sellerHandle;
  }

  State state() {
    return state;
  }

  long openingAmountCents() {
    return openingAmountCents;
  }

  long minimumIncrementCents() {
    return minimumIncrementCents;
  }

  long currentAmountCents() {
    return currentAmountCents;
  }

  long eligibleBidCount() {
    return eligibleBidCount;
  }

  boolean reserveMet() {
    return eligibleBidCount > 0
        && (reserveAmountCents == null || currentAmountCents >= reserveAmountCents);
  }

  void recordAcceptedBid(long amountCents, Instant acceptedAt) {
    currentAmountCents = amountCents;
    acceptedBidCount++;
    eligibleBidCount++;
    Instant extendedEndAt = acceptedAt.plusSeconds(policySnapshot.protectionWindowSeconds());
    if (!acceptedAt.isBefore(endsAt.minusSeconds(policySnapshot.protectionWindowSeconds()))
        && extendedEndAt.isAfter(endsAt)) {
      endsAt = extendedEndAt;
    }
  }

  void recalculateEligibleBidProjection(long eligibleBidCount, long currentAmountCents) {
    if (eligibleBidCount < 0
        || currentAmountCents < openingAmountCents
        || currentAmountCents > 100_000_000L) {
      throw new IllegalArgumentException("The eligible bid projection is invalid.");
    }
    this.eligibleBidCount = eligibleBidCount;
    this.currentAmountCents = currentAmountCents;
  }

  long nextMinimumAmountCents() {
    if (eligibleBidCount == 0) {
      return openingAmountCents;
    }
    return Math.min(100_000_001L, Math.addExact(currentAmountCents, minimumIncrementCents));
  }

  Instant effectiveEndAt() {
    return endsAt;
  }

  Long reserveAmountCents() {
    return reserveAmountCents;
  }

  Instant startsAt() {
    return startsAt;
  }

  Instant endsAt() {
    return endsAt;
  }

  Instant scheduledAt() {
    return scheduledAt;
  }

  Instant endedAt() {
    return endedAt;
  }

  String cancellationReason() {
    return cancellationReason;
  }

  UUID finalBidId() {
    return finalBidId;
  }

  UUID finalBidderId() {
    return finalBidderId;
  }

  String finalBidderPseudonym() {
    return finalBidderPseudonym;
  }

  Long finalAmountCents() {
    return finalAmountCents;
  }

  Instant finalOutcomeRecordedAt() {
    return finalOutcomeRecordedAt;
  }

  State suspensionSourceState() {
    return suspensionSourceState;
  }

  Instant suspendedAt() {
    return suspendedAt;
  }

  Duration remainingDuration() {
    return remainingDurationMillis == null ? null : Duration.ofMillis(remainingDurationMillis);
  }

  List<AuctionTimelineEntry> timeline() {
    return List.copyOf(timeline);
  }

  AuctionItemSnapshot itemSnapshot() {
    return itemSnapshot;
  }

  AuctionPolicySnapshot policySnapshot() {
    return policySnapshot;
  }

  List<AuctionItemSnapshotMedia> snapshotMedia() {
    return List.copyOf(snapshotMedia);
  }

  enum State {
    DRAFT,
    SCHEDULED,
    LIVE,
    SUSPENDED,
    CLOSING,
    AWAITING_SELLER_DECISION,
    SOLD,
    UNSOLD,
    CANCELLED
  }
}
