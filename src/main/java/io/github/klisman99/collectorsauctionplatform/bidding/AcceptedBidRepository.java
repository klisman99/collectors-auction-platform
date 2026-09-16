package io.github.klisman99.collectorsauctionplatform.bidding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AcceptedBidRepository extends JpaRepository<AcceptedBid, UUID> {

  Optional<AcceptedBid> findFirstByAuctionIdOrderBySequenceDesc(UUID auctionId);

  List<AcceptedBid> findAllByAuctionIdOrderBySequenceDesc(UUID auctionId);

  @Query(
      "select bid from AcceptedBid bid where bid.auctionId = :auctionId and not exists (select disqualification from BidDisqualification disqualification where disqualification.acceptedBidId = bid.id) order by bid.sequence desc")
  List<AcceptedBid> findAllEligibleByAuctionIdOrderBySequenceDesc(UUID auctionId);

  @Query(
      "select bid from AcceptedBid bid where bid.bidderId = :bidderId order by bid.auctionId, bid.sequence")
  List<AcceptedBid> findAllByBidderIdOrderByAuctionIdAndSequence(UUID bidderId);

  @Query("select distinct bid.auctionId from AcceptedBid bid where bid.bidderId = :bidderId")
  List<UUID> findDistinctAuctionIdsByBidderId(UUID bidderId);
}
