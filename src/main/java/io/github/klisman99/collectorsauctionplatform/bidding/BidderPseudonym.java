package io.github.klisman99.collectorsauctionplatform.bidding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "bidder_pseudonyms")
class BidderPseudonym {

  @Id private UUID id;

  @Column(name = "auction_id", nullable = false, updatable = false)
  private UUID auctionId;

  @Column(name = "bidder_id", nullable = false, updatable = false)
  private UUID bidderId;

  @Column(nullable = false, updatable = false)
  private String pseudonym;

  protected BidderPseudonym() {}

  BidderPseudonym(UUID auctionId, UUID bidderId) {
    this.id = UUID.randomUUID();
    this.auctionId = auctionId;
    this.bidderId = bidderId;
    this.pseudonym = "Bidder-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  String pseudonym() {
    return pseudonym;
  }
}
