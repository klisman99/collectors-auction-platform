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
import jakarta.persistence.Table;
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

  @Column(name = "reserve_amount_cents")
  private Long reserveAmountCents;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "scheduled_at", nullable = false, updatable = false)
  private Instant scheduledAt;

  @Embedded private AuctionItemSnapshot itemSnapshot;

  @Embedded private AuctionPolicySnapshot policySnapshot;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "auction_item_snapshot_media",
      joinColumns = @JoinColumn(name = "auction_id"))
  @OrderBy("sortOrder")
  private List<AuctionItemSnapshotMedia> snapshotMedia = new ArrayList<>();

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
    auction.reserveAmountCents = command.reserveAmountCents();
    auction.startsAt = command.startsAt();
    auction.endsAt = command.endsAt();
    auction.scheduledAt = now;
    auction.itemSnapshot = AuctionItemSnapshot.from(source);
    auction.policySnapshot = policy;
    auction.snapshotMedia = source.media().stream().map(AuctionItemSnapshotMedia::from).toList();
    return auction;
  }

  void updateEditableTerms(Long reserveAmountCents, Instant startsAt, Instant endsAt, Instant now) {
    if (state != State.SCHEDULED || !now.isBefore(this.startsAt)) {
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
