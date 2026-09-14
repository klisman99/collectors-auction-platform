package io.github.klisman99.collectorsauctionplatform.bidding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AcceptedBidRepository extends JpaRepository<AcceptedBid, UUID> {

  Optional<AcceptedBid> findFirstByAuctionIdOrderBySequenceDesc(UUID auctionId);

  List<AcceptedBid> findAllByAuctionIdOrderBySequenceDesc(UUID auctionId);
}
