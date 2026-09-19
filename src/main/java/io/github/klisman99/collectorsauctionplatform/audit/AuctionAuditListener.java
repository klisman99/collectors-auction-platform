package io.github.klisman99.collectorsauctionplatform.audit;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionLifecycleEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class AuctionAuditListener {

  private final AuditProjectionStore auditRecords;

  AuctionAuditListener(AuditProjectionStore auditRecords) {
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
          case SOLD -> AuditRecord.AuditAction.AUCTION_SOLD;
          case UNSOLD -> AuditRecord.AuditAction.AUCTION_UNSOLD;
          case AWAITING_SELLER_DECISION -> AuditRecord.AuditAction.AUCTION_AWAITING_SELLER_DECISION;
          case SELLER_DECISION_ACCEPTED -> AuditRecord.AuditAction.AUCTION_SELLER_DECISION_ACCEPTED;
          case SELLER_DECISION_REJECTED -> AuditRecord.AuditAction.AUCTION_SELLER_DECISION_REJECTED;
          case SELLER_DECISION_EXPIRED -> AuditRecord.AuditAction.AUCTION_SELLER_DECISION_EXPIRED;
          case SELLER_DECISION_REOPENED -> AuditRecord.AuditAction.AUCTION_SELLER_DECISION_REOPENED;
          case SELLER_DECISION_NO_ELIGIBLE_BID ->
              AuditRecord.AuditAction.AUCTION_SELLER_DECISION_NO_ELIGIBLE_BID;
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
            + valueMetadata("finalAmountCents", event.finalAmountCents())
            + valueMetadata("winningBidderId", event.winningBidderId())
            + valueMetadata("winningBidderHandle", event.winningBidderHandle())
            + valueMetadata("sellerDecisionDeadlineAt", event.sellerDecisionDeadlineAt())
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
                    || event.type() == AuctionLifecycleEvent.Type.SOLD
                    || event.type() == AuctionLifecycleEvent.Type.UNSOLD
                    || event.type() == AuctionLifecycleEvent.Type.AWAITING_SELLER_DECISION
                    || event.type() == AuctionLifecycleEvent.Type.SELLER_DECISION_EXPIRED
                    || event.type() == AuctionLifecycleEvent.Type.SELLER_DECISION_REOPENED
                    || event.type() == AuctionLifecycleEvent.Type.SELLER_DECISION_NO_ELIGIBLE_BID
                ? AuditRecord.systemAuctionAction(
                    action, event.auctionId(), event.occurredAt(), metadata)
                : AuditRecord.accountAuctionAction(
                    action, event.sellerId(), event.auctionId(), event.occurredAt(), metadata);
    auditRecords.append(
        record
            .participates(event.sellerId(), event.winningBidderId())
            .forAuction(event.auctionId())
            .forItem(event.itemId())
            .withPublicDetails(
                event.reasonCategory() == null ? null : event.reasonCategory().name(),
                event.publicReason(),
                event.finalAmountCents(),
                null,
                event.internalNote()));
  }

  private String valueMetadata(String name, Object value) {
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
