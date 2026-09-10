package io.github.klisman99.collectorsauctionplatform.auctions;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface AuctionRepository extends JpaRepository<Auction, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Auction> findByIdAndSellerId(UUID id, UUID sellerId);

  List<Auction> findAllByStateOrderByStartsAtAsc(Auction.State state);
}
