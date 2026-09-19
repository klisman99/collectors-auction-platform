package io.github.klisman99.collectorsauctionplatform.audit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {

  List<AuditRecord> findAllByOrderByOccurredAtDesc();

  boolean existsBySourceFingerprint(String sourceFingerprint);

  Page<AuditRecord> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

  @Query(
      value =
          "select distinct record from AuditRecord record join record.participantAccountIds participant "
              + "where participant = :accountId order by record.occurredAt desc, record.id desc",
      countQuery =
          "select count(distinct record) from AuditRecord record join record.participantAccountIds participant "
              + "where participant = :accountId")
  Page<AuditRecord> findParticipantHistory(@Param("accountId") UUID accountId, Pageable pageable);

  Page<AuditRecord> findAllByActionInOrderByOccurredAtDescIdDesc(
      Collection<AuditRecord.AuditAction> actions, Pageable pageable);

  Page<AuditRecord> findAllByAuctionIdAndActionInOrderByOccurredAtDescIdDesc(
      UUID auctionId, Collection<AuditRecord.AuditAction> actions, Pageable pageable);
}
