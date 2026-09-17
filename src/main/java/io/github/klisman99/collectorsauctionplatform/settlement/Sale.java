package io.github.klisman99.collectorsauctionplatform.settlement;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionSold;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales")
class Sale {

  @Id private UUID id;

  @Column(name = "auction_id", nullable = false, unique = true, updatable = false)
  private UUID auctionId;

  @Column(name = "item_id", nullable = false, updatable = false)
  private UUID itemId;

  @Column(name = "seller_id", nullable = false, updatable = false)
  private UUID sellerId;

  @Column(name = "buyer_id", nullable = false, updatable = false)
  private UUID buyerId;

  @Column(name = "amount_cents", nullable = false, updatable = false)
  private long amountCents;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Sale() {}

  private Sale(AuctionSold auction) {
    this.id = UUID.randomUUID();
    this.auctionId = auction.auctionId();
    this.itemId = auction.itemId();
    this.sellerId = auction.sellerId();
    this.buyerId = auction.buyerId();
    this.amountCents = auction.amountCents();
    this.createdAt = auction.occurredAt();
  }

  static Sale from(AuctionSold auction) {
    return new Sale(auction);
  }
}
