package io.github.klisman99.collectorsauctionplatform.audit;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditQueryService {

  private final AuditRecordRepository auditRecords;

  AuditQueryService(AuditRecordRepository auditRecords) {
    this.auditRecords = auditRecords;
  }

  @Transactional(readOnly = true)
  List<AuditRecord> listAll() {
    return auditRecords.findAllByOrderByOccurredAtDesc();
  }
}
