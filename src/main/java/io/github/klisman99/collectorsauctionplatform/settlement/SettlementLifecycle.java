package io.github.klisman99.collectorsauctionplatform.settlement;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementLifecycle {

  private final SaleRepository sales;
  private final CatalogAuctioning catalog;
  private final Clock clock;
  private final SaleTransitionPublisher transitions;

  SettlementLifecycle(
      SaleRepository sales,
      CatalogAuctioning catalog,
      Clock clock,
      SaleTransitionPublisher transitions) {
    this.sales = sales;
    this.catalog = catalog;
    this.clock = clock;
    this.transitions = transitions;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(fixedDelayString = "${platform.settlement.reconciliation-delay-ms:1000}")
  @Transactional
  public void reconcileDueSales() {
    Instant now = Instant.now(clock);
    Set<Sale> due = new LinkedHashSet<>(sales.findDuePaymentForUpdate(now));
    due.addAll(sales.findDueShipmentForUpdate(now));
    due.addAll(sales.findDueDeliveryConfirmationForUpdate(now));
    for (Sale sale : due) {
      if (sale.expirePaymentIfDue(now)) {
        catalog.releaseApprovedItemAfterFailedSettlement(sale.itemId(), now);
        transitions.publish(sale, SaleTransitionEvent.Type.PAYMENT_EXPIRED, null, now);
      } else if (sale.expireShipmentIfDue(now)) {
        catalog.releaseApprovedItemAfterFailedSettlement(sale.itemId(), now);
        transitions.publish(sale, SaleTransitionEvent.Type.SHIPMENT_EXPIRED, null, now);
      } else if (sale.completeDeliveryIfDue(now)) {
        catalog.archiveApprovedItemAfterCompletedSettlement(sale.itemId(), now);
        transitions.publish(
            sale, SaleTransitionEvent.Type.DELIVERY_CONFIRMATION_DEADLINE_EXPIRED, null, now);
      }
    }
  }
}
