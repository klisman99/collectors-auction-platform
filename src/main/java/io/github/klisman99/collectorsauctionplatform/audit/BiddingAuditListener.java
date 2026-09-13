package io.github.klisman99.collectorsauctionplatform.audit;

import io.github.klisman99.collectorsauctionplatform.bidding.BidAccepted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class BiddingAuditListener {

  private final AuditRecordRepository auditRecords;

  BiddingAuditListener(AuditRecordRepository auditRecords) {
    this.auditRecords = auditRecords;
  }

  @ApplicationModuleListener
  void auditAcceptedBid(BidAccepted event) {
    auditRecords.save(
        AuditRecord.accountAuctionAction(
            AuditRecord.AuditAction.BID_ACCEPTED,
            event.bidderId(),
            event.auctionId(),
            event.acceptedAt(),
            "amountCents="
                + event.amountCents()
                + ";sequence="
                + event.sequence()
                + ";bidderPseudonym="
                + event.bidderPseudonym()));
  }
}
