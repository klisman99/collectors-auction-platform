package io.github.klisman99.collectorsauctionplatform.audit;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {
}
