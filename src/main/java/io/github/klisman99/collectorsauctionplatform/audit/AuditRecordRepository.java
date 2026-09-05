package io.github.klisman99.collectorsauctionplatform.audit;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {

    List<AuditRecord> findTop100ByOrderByOccurredAtDesc();
}
