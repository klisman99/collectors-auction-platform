package io.github.klisman99.collectorsauctionplatform.settlement;

import java.time.Instant;
import java.util.UUID;

/** Durable fact emitted after a sale transition is persisted. */
public record SaleTransitionEvent(
    UUID saleId,
    UUID auctionId,
    UUID itemId,
    String itemTitle,
    UUID sellerId,
    String sellerHandle,
    String sellerEmail,
    UUID buyerId,
    String buyerHandle,
    String buyerEmail,
    long amountCents,
    String state,
    Type type,
    UUID actorId,
    Instant occurredAt,
    Instant paymentDeadlineAt,
    Instant shipmentDeadlineAt,
    String carrier,
    String trackingReference) {

  public enum Type {
    CREATED,
    PAYMENT_RECORDED,
    SHIPMENT_RECORDED,
    PAYMENT_EXPIRED,
    SHIPMENT_EXPIRED
  }
}
