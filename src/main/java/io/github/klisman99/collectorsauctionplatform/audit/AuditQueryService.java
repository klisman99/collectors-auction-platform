package io.github.klisman99.collectorsauctionplatform.audit;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditQueryService {

  private static final int MAXIMUM_PAGE_SIZE = 50;

  private static final EnumSet<AuditRecord.AuditAction> MODERATOR_ACTIONS =
      EnumSet.of(
          AuditRecord.AuditAction.COLLECTIBLE_APPROVED,
          AuditRecord.AuditAction.COLLECTIBLE_REJECTED,
          AuditRecord.AuditAction.AUCTION_SUSPENDED,
          AuditRecord.AuditAction.AUCTION_RELEASED,
          AuditRecord.AuditAction.AUCTION_RESUMED,
          AuditRecord.AuditAction.AUCTION_ADMINISTRATIVELY_CANCELLED);

  private static final EnumSet<AuditRecord.AuditAction> PUBLIC_AUCTION_ACTIONS =
      EnumSet.of(
          AuditRecord.AuditAction.AUCTION_SCHEDULED,
          AuditRecord.AuditAction.AUCTION_RESCHEDULED,
          AuditRecord.AuditAction.AUCTION_STARTED,
          AuditRecord.AuditAction.AUCTION_CANCELLED,
          AuditRecord.AuditAction.AUCTION_ENDED,
          AuditRecord.AuditAction.AUCTION_SOLD,
          AuditRecord.AuditAction.AUCTION_UNSOLD,
          AuditRecord.AuditAction.AUCTION_AWAITING_SELLER_DECISION,
          AuditRecord.AuditAction.AUCTION_SELLER_DECISION_ACCEPTED,
          AuditRecord.AuditAction.AUCTION_SELLER_DECISION_REJECTED,
          AuditRecord.AuditAction.AUCTION_SELLER_DECISION_EXPIRED,
          AuditRecord.AuditAction.AUCTION_SELLER_DECISION_REOPENED,
          AuditRecord.AuditAction.AUCTION_SELLER_DECISION_NO_ELIGIBLE_BID,
          AuditRecord.AuditAction.AUCTION_SUSPENDED,
          AuditRecord.AuditAction.AUCTION_RELEASED,
          AuditRecord.AuditAction.AUCTION_RESUMED,
          AuditRecord.AuditAction.AUCTION_ADMINISTRATIVELY_CANCELLED,
          AuditRecord.AuditAction.BID_ACCEPTED,
          AuditRecord.AuditAction.BID_DISQUALIFIED);

  private final AuditRecordRepository auditRecords;

  AuditQueryService(AuditRecordRepository auditRecords) {
    this.auditRecords = auditRecords;
  }

  @Transactional(readOnly = true)
  List<AuditRecord> listAll() {
    return auditRecords.findAllByOrderByOccurredAtDesc();
  }

  @Transactional(readOnly = true)
  Page<AuditRecord> listParticipantHistory(UUID accountId, int page, int size) {
    return auditRecords.findParticipantHistory(accountId, pageable(page, size));
  }

  @Transactional(readOnly = true)
  Page<AuditRecord> listModeratorHistory(int page, int size) {
    return auditRecords.findAllByActionInOrderByOccurredAtDescIdDesc(
        MODERATOR_ACTIONS, pageable(page, size));
  }

  @Transactional(readOnly = true)
  Page<AuditRecord> listAdministratorHistory(int page, int size) {
    return auditRecords.findAllByOrderByOccurredAtDescIdDesc(pageable(page, size));
  }

  @Transactional(readOnly = true)
  Page<AuditRecord> listPublicAuctionTimeline(UUID auctionId, int page, int size) {
    return auditRecords.findAllByAuctionIdAndActionInOrderByOccurredAtDescIdDesc(
        auctionId, PUBLIC_AUCTION_ACTIONS, pageable(page, size));
  }

  private PageRequest pageable(int page, int size) {
    if (page < 0 || size < 1 || size > MAXIMUM_PAGE_SIZE) {
      throw AuditApiException.invalidHistoryPage();
    }
    return PageRequest.of(page, size);
  }
}
