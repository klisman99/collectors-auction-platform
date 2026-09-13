package io.github.klisman99.collectorsauctionplatform.bidding;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BidderPseudonymRepository extends JpaRepository<BidderPseudonym, UUID> {

  Optional<BidderPseudonym> findByAuctionIdAndBidderId(UUID auctionId, UUID bidderId);
}
