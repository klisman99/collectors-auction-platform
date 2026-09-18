package io.github.klisman99.collectorsauctionplatform.settlement;

import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SaleService {

  private final SaleRepository sales;
  private final AccountDirectory accounts;
  private final Clock clock;
  private final SaleTransitionPublisher transitions;

  SaleService(
      SaleRepository sales,
      AccountDirectory accounts,
      Clock clock,
      SaleTransitionPublisher transitions) {
    this.sales = sales;
    this.accounts = accounts;
    this.clock = clock;
    this.transitions = transitions;
  }

  @Transactional(readOnly = true)
  List<Sale> listForParticipant(UUID participantId) {
    return sales.findAllByBuyerIdOrSellerIdOrderByCreatedAtDesc(participantId, participantId);
  }

  @Transactional
  Sale recordPayment(UUID buyerId, UUID saleId) {
    accounts.lockRegularAccount(buyerId);
    Sale sale = saleForUpdate(saleId);
    if (!sale.isBuyer(buyerId)) {
      throw SaleApiException.paymentForbidden();
    }
    Instant now = Instant.now(clock);
    Sale.Transition transition = sale.payment(now);
    switch (transition) {
      case APPLIED ->
          transitions.publish(sale, SaleTransitionEvent.Type.PAYMENT_RECORDED, buyerId, now);
      case EXPIRED ->
          transitions.publish(sale, SaleTransitionEvent.Type.PAYMENT_EXPIRED, null, now);
      case IDEMPOTENT -> {
        // A retried completed command returns the same durable sale state.
      }
      case UNAVAILABLE -> throw SaleApiException.paymentUnavailable();
    }
    return sale;
  }

  @Transactional
  Sale recordShipment(UUID sellerId, UUID saleId, String carrier, String trackingReference) {
    accounts.lockRegularAccount(sellerId);
    Sale sale = saleForUpdate(saleId);
    if (!sale.isSeller(sellerId)) {
      throw SaleApiException.shipmentForbidden();
    }
    Instant now = Instant.now(clock);
    Sale.Transition transition = sale.shipment(carrier.trim(), trackingReference.trim(), now);
    switch (transition) {
      case APPLIED ->
          transitions.publish(sale, SaleTransitionEvent.Type.SHIPMENT_RECORDED, sellerId, now);
      case EXPIRED ->
          transitions.publish(sale, SaleTransitionEvent.Type.SHIPMENT_EXPIRED, null, now);
      case IDEMPOTENT -> {
        // A retried completed command returns the same durable sale state.
      }
      case UNAVAILABLE -> throw SaleApiException.shipmentUnavailable();
    }
    return sale;
  }

  private Sale saleForUpdate(UUID saleId) {
    return sales.findByIdForUpdate(saleId).orElseThrow(SaleApiException::notFound);
  }
}
