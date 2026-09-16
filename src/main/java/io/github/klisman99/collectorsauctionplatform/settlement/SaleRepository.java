package io.github.klisman99.collectorsauctionplatform.settlement;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SaleRepository extends JpaRepository<Sale, UUID> {

  boolean existsByAuctionId(UUID auctionId);
}
