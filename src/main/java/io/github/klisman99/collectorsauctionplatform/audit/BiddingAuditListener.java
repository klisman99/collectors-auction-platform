package io.github.klisman99.collectorsauctionplatform.audit;

import io.github.klisman99.collectorsauctionplatform.bidding.BidAccepted;
import io.github.klisman99.collectorsauctionplatform.bidding.BidDisqualified;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class BiddingAuditListener {

  private final AuditProjectionStore auditRecords;

  BiddingAuditListener(AuditProjectionStore auditRecords) {
    this.auditRecords = auditRecords;
  }

  @ApplicationModuleListener
  void auditAcceptedBid(BidAccepted event) {
    auditRecords.append(
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
                    + event.bidderPseudonym())
            .participates(event.bidderId())
            .forAuction(event.auctionId())
            .withPublicDetails(null, null, event.amountCents(), event.bidderPseudonym(), null));
  }

  @ApplicationModuleListener
  void auditBidDisqualification(BidDisqualified event) {
    auditRecords.append(
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
                    + event.internalNote())
            .participates(event.bidderId())
            .forAuction(event.auctionId())
            .withPublicDetails(
                event.reasonCategory(), event.publicReason(), null, null, event.internalNote()));
  }
}
