package io.github.klisman99.collectorsauctionplatform.bidding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BidAttemptRepository extends JpaRepository<BidAttempt, UUID> {

  List<BidAttempt> findAllByAuctionIdAndBidderIdAndIdempotencyKeyOrderByReceivedAtAsc(
      UUID auctionId, UUID bidderId, UUID idempotencyKey);

  long countByAuctionIdAndBidderIdAndReceivedAtGreaterThanEqual(
      UUID auctionId, UUID bidderId, Instant threshold);
}
