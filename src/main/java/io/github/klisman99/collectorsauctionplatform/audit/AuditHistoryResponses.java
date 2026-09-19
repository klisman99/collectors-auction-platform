package io.github.klisman99.collectorsauctionplatform.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A deterministic page of role-filtered audit history.")
record AuditHistoryPageResponse(
    List<AuditHistoryEventResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {}

@Schema(description = "A safe, append-only fact visible in a history timeline.")
record AuditHistoryEventResponse(
    UUID id,
    String action,
    String targetType,
    UUID targetId,
    UUID auctionId,
    UUID itemId,
    UUID saleId,
    Instant occurredAt,
    String reasonCategory,
    String publicReason,
    Long amountCents,
    String bidderPseudonym,
    String internalNote) {

  static AuditHistoryEventResponse publicView(AuditRecord record) {
    return from(record, false);
  }

  static AuditHistoryEventResponse participantView(AuditRecord record) {
    return from(record, false);
  }

  static AuditHistoryEventResponse moderatorView(AuditRecord record) {
    return from(record, true);
  }

  private static AuditHistoryEventResponse from(AuditRecord record, boolean includeInternalNote) {
    return new AuditHistoryEventResponse(
        record.id(),
        record.action().name(),
        record.targetType().name(),
        record.targetId(),
        record.auctionId(),
        record.itemId(),
        record.saleId(),
        record.occurredAt(),
        record.reasonCategory(),
        record.publicReason(),
        record.amountCents(),
        record.bidderPseudonym(),
        includeInternalNote ? record.internalNote() : null);
  }
}

@Schema(description = "A complete administrator-visible page of audit history.")
record AdministrativeAuditHistoryPageResponse(
    List<AdministrativeAuditHistoryEventResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {}

@Schema(description = "An administrator-visible immutable audit record.")
record AdministrativeAuditHistoryEventResponse(
    UUID id,
    String actorType,
    UUID actorId,
    String action,
    String targetType,
    UUID targetId,
    UUID auctionId,
    UUID itemId,
    UUID saleId,
    Instant occurredAt,
    String reasonCategory,
    String publicReason,
    String internalNote,
    Long amountCents,
    String bidderPseudonym,
    String metadata) {

  static AdministrativeAuditHistoryEventResponse from(AuditRecord record) {
    return new AdministrativeAuditHistoryEventResponse(
        record.id(),
        record.actorType().name(),
        record.actorId(),
        record.action().name(),
        record.targetType().name(),
        record.targetId(),
        record.auctionId(),
        record.itemId(),
        record.saleId(),
        record.occurredAt(),
        record.reasonCategory(),
        record.publicReason(),
        record.internalNote(),
        record.amountCents(),
        record.bidderPseudonym(),
        record.metadata());
  }
}
