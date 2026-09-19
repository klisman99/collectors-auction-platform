package io.github.klisman99.collectorsauctionplatform.audit;

import io.github.klisman99.collectorsauctionplatform.settlement.SaleTransitionEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class SettlementAuditListener {

  private final AuditProjectionStore auditRecords;

  SettlementAuditListener(AuditProjectionStore auditRecords) {
    this.auditRecords = auditRecords;
  }

  @ApplicationModuleListener
  void auditTransition(SaleTransitionEvent event) {
    AuditRecord.AuditAction action =
        switch (event.type()) {
          case CREATED -> AuditRecord.AuditAction.SALE_CREATED;
          case PAYMENT_RECORDED -> AuditRecord.AuditAction.SALE_PAYMENT_RECORDED;
          case SHIPMENT_RECORDED -> AuditRecord.AuditAction.SALE_SHIPMENT_RECORDED;
          case PAYMENT_EXPIRED -> AuditRecord.AuditAction.SALE_PAYMENT_EXPIRED;
          case SHIPMENT_EXPIRED -> AuditRecord.AuditAction.SALE_SHIPMENT_EXPIRED;
          case DELIVERY_CONFIRMED -> AuditRecord.AuditAction.SALE_DELIVERY_CONFIRMED;
          case DELIVERY_CONFIRMATION_DEADLINE_EXPIRED ->
              AuditRecord.AuditAction.SALE_DELIVERY_CONFIRMATION_DEADLINE_EXPIRED;
        };
    String metadata =
        "auctionId="
            + event.auctionId()
            + ";itemId="
            + event.itemId()
            + ";buyerId="
            + event.buyerId()
            + ";sellerId="
            + event.sellerId()
            + ";amountCents="
            + event.amountCents()
            + value("paymentDeadlineAt", event.paymentDeadlineAt())
            + value("shipmentDeadlineAt", event.shipmentDeadlineAt())
            + value("deliveryConfirmationDeadlineAt", event.deliveryConfirmationDeadlineAt())
            + value("completedAt", event.completedAt())
            + value("terminalReason", event.terminalReason())
            + value("itemDisposition", event.itemDisposition())
            + value("carrier", event.carrier())
            + value("trackingReference", event.trackingReference());
    AuditRecord record =
        event.actorId() == null
            ? AuditRecord.systemSaleAction(action, event.saleId(), event.occurredAt(), metadata)
            : AuditRecord.accountSaleAction(
                action, event.actorId(), event.saleId(), event.occurredAt(), metadata);
    auditRecords.append(
        record
            .participates(event.buyerId(), event.sellerId())
            .forAuction(event.auctionId())
            .forItem(event.itemId())
            .forSale(event.saleId())
            .withPublicDetails(null, null, event.amountCents(), null, null));
  }

  private String value(String name, Object value) {
    return value == null ? "" : ";" + name + "=" + value;
  }
}
