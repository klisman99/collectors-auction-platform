package io.github.klisman99.collectorsauctionplatform.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists a durable audit projection exactly once when a domain event is replayed. */
@Service
class AuditProjectionStore {

  private final AuditRecordRepository auditRecords;

  AuditProjectionStore(AuditRecordRepository auditRecords) {
    this.auditRecords = auditRecords;
  }

  @Transactional
  void append(AuditRecord record) {
    if (!auditRecords.existsBySourceFingerprint(record.sourceFingerprint())) {
      auditRecords.save(record);
    }
  }
}
