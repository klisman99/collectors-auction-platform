package io.github.klisman99.collectorsauctionplatform.audit;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionLifecycleEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class AuctionAuditListener {

  private final AuditRecordRepository auditRecords;

  AuctionAuditListener(AuditRecordRepository auditRecords) {
    this.auditRecords = auditRecords;
  }

  @ApplicationModuleListener
  void auditLifecycle(AuctionLifecycleEvent event) {
    AuditRecord.AuditAction action =
        switch (event.type()) {
          case SCHEDULED -> AuditRecord.AuditAction.AUCTION_SCHEDULED;
          case RESCHEDULED -> AuditRecord.AuditAction.AUCTION_RESCHEDULED;
          case STARTED -> AuditRecord.AuditAction.AUCTION_STARTED;
          case CANCELLED -> AuditRecord.AuditAction.AUCTION_CANCELLED;
          case ENDED -> AuditRecord.AuditAction.AUCTION_ENDED;
          case SUSPENDED -> AuditRecord.AuditAction.AUCTION_SUSPENDED;
          case RELEASED -> AuditRecord.AuditAction.AUCTION_RELEASED;
          case RESUMED -> AuditRecord.AuditAction.AUCTION_RESUMED;
          case ADMINISTRATIVELY_CANCELLED ->
              AuditRecord.AuditAction.AUCTION_ADMINISTRATIVELY_CANCELLED;
        };
    String metadata =
        "itemId="
            + event.itemId()
            + termsMetadata("previous", event.previousTerms())
            + termsMetadata("current", event.currentTerms())
            + valueMetadata("reasonCategory", event.reasonCategory())
            + valueMetadata("publicReason", event.publicReason())
            + valueMetadata("internalNote", event.internalNote())
            + valueMetadata("itemDisposition", event.itemDisposition());
    AuditRecord record =
        event.operationalActorId() != null
            ? AuditRecord.operationalAuctionAction(
                action, event.operationalActorId(), event.auctionId(), event.occurredAt(), metadata)
            : event.type() == AuctionLifecycleEvent.Type.STARTED
                    || event.type() == AuctionLifecycleEvent.Type.ENDED
                ? AuditRecord.systemAuctionAction(
                    action, event.auctionId(), event.occurredAt(), metadata)
                : AuditRecord.accountAuctionAction(
                    action, event.sellerId(), event.auctionId(), event.occurredAt(), metadata);
    auditRecords.save(record);
  }

  private String valueMetadata(String name, String value) {
    return value == null ? "" : ";" + name + "=" + value;
  }

  private String termsMetadata(String prefix, AuctionLifecycleEvent.PublishedTerms publishedTerms) {
    if (publishedTerms == null) {
      return "";
    }
    return ";"
        + prefix
        + "ReserveAmountCents="
        + (publishedTerms.reserveAmountCents() == null
            ? "none"
            : publishedTerms.reserveAmountCents())
        + ";"
        + prefix
        + "StartsAt="
        + publishedTerms.startsAt()
        + ";"
        + prefix
        + "EndsAt="
        + publishedTerms.endsAt();
  }
}
