package io.github.klisman99.collectorsauctionplatform.audit;

import io.github.klisman99.collectorsauctionplatform.bidding.BidAccepted;
import io.github.klisman99.collectorsauctionplatform.bidding.BidDisqualified;
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

  @ApplicationModuleListener
  void auditBidDisqualification(BidDisqualified event) {
    auditRecords.save(
        AuditRecord.operationalAcceptedBidAction(
            AuditRecord.AuditAction.BID_DISQUALIFIED,
            event.actorId(),
            event.acceptedBidId(),
            event.disqualifiedAt(),
            "auctionId="
                + event.auctionId()
                + ";bidderId="
                + event.bidderId()
                + ";reasonCategory="
                + event.reasonCategory()
                + ";publicReason="
                + event.publicReason()
                + ";internalNote="
                + event.internalNote()));
  }
}
