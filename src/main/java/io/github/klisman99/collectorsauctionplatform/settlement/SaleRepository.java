package io.github.klisman99.collectorsauctionplatform.settlement;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SaleRepository extends JpaRepository<Sale, UUID> {

  boolean existsByAuctionId(UUID auctionId);

  List<Sale> findAllByBuyerIdOrSellerIdOrderByCreatedAtDesc(UUID buyerId, UUID sellerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select sale from Sale sale where sale.id = :id")
  Optional<Sale> findByIdForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select sale from Sale sale where sale.state = 'PAYMENT_PENDING' and sale.paymentDeadlineAt <= :now order by sale.paymentDeadlineAt")
  List<Sale> findDuePaymentForUpdate(@Param("now") Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select sale from Sale sale where sale.state = 'SHIPMENT_PENDING' and sale.shipmentDeadlineAt <= :now order by sale.shipmentDeadlineAt")
  List<Sale> findDueShipmentForUpdate(@Param("now") Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select sale from Sale sale where sale.state = 'SHIPPED' and sale.deliveryConfirmationDeadlineAt <= :now order by sale.deliveryConfirmationDeadlineAt")
  List<Sale> findDueDeliveryConfirmationForUpdate(@Param("now") Instant now);
}
