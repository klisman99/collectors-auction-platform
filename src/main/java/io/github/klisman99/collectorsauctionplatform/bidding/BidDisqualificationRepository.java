package io.github.klisman99.collectorsauctionplatform.bidding;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BidDisqualificationRepository extends JpaRepository<BidDisqualification, UUID> {

  List<BidDisqualification> findAllByAcceptedBidIdIn(Collection<UUID> acceptedBidIds);
}
