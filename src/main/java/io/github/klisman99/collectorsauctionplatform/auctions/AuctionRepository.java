package io.github.klisman99.collectorsauctionplatform.auctions;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuctionRepository extends JpaRepository<Auction, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Auction> findByIdAndSellerId(UUID id, UUID sellerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select auction from Auction auction where auction.id = :id")
  Optional<Auction> findByIdForUpdate(@Param("id") UUID id);

  List<Auction> findAllByStateOrderByStartsAtAsc(Auction.State state);

  Page<Auction> findAllByState(Auction.State state, Pageable pageable);

  @Query(
      "select auction from Auction auction where auction.state = 'SCHEDULED' or (auction.state = 'SUSPENDED' and auction.suspensionSourceState = 'SCHEDULED')")
  Page<Auction> findScheduledDiscovery(Pageable pageable);

  @Query(
      "select auction from Auction auction where auction.state = 'LIVE' or (auction.state = 'SUSPENDED' and auction.suspensionSourceState = 'LIVE')")
  Page<Auction> findLiveDiscovery(Pageable pageable);

  Page<Auction> findAllByStateIn(List<Auction.State> states, Pageable pageable);

  List<Auction> findAllBySellerIdAndStateInOrderByStartsAtAsc(
      UUID sellerId, List<Auction.State> states);

  List<Auction> findAllByStateOrderBySuspendedAtAsc(Auction.State state);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select auction from Auction auction where auction.state = 'SCHEDULED' and auction.startsAt <= :now order by auction.startsAt")
  List<Auction> findDueScheduledForUpdate(@Param("now") Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select auction from Auction auction where auction.state = 'LIVE' and auction.endsAt <= :now order by auction.endsAt")
  List<Auction> findDueLiveForUpdate(@Param("now") Instant now);
}
