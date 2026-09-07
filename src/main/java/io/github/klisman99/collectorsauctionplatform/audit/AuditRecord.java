package io.github.klisman99.collectorsauctionplatform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_records")
class AuditRecord {

  @Id private UUID id;

  @Column(name = "actor_type", nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  private ActorType actorType;

  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  private AuditAction action;

  @Column(name = "target_type", nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  private TargetType targetType;

  @Column(name = "target_id", nullable = false, updatable = false)
  private UUID targetId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(nullable = false, updatable = false)
  private String metadata;

  protected AuditRecord() {}

  private AuditRecord(
      ActorType actorType,
      UUID actorId,
      AuditAction action,
      TargetType targetType,
      UUID targetId,
      Instant occurredAt,
      String metadata) {
    this.id = UUID.randomUUID();
    this.actorType = actorType;
    this.actorId = actorId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.occurredAt = occurredAt;
    this.metadata = metadata;
  }

  static AuditRecord systemAction(
      AuditAction action, UUID targetId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.SYSTEM, null, action, TargetType.REGULAR_ACCOUNT, targetId, occurredAt, metadata);
  }

  static AuditRecord accountAction(
      AuditAction action, UUID accountId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.REGULAR_ACCOUNT,
        accountId,
        action,
        TargetType.REGULAR_ACCOUNT,
        accountId,
        occurredAt,
        metadata);
  }

  enum ActorType {
    SYSTEM,
    REGULAR_ACCOUNT
  }

  enum AuditAction {
    REGULAR_ACCOUNT_REGISTERED,
    REGULAR_ACCOUNT_VERIFIED,
    REGULAR_ACCOUNT_PASSWORD_RESET
  }

  enum TargetType {
    REGULAR_ACCOUNT
  }
}
