package io.github.klisman99.collectorsauctionplatform.audit;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
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

  @Column(nullable = false, updatable = false, length = 4000)
  private String metadata;

  @Column(name = "source_fingerprint", nullable = false, updatable = false, length = 64)
  private String sourceFingerprint;

  @Column(name = "auction_id", updatable = false)
  private UUID auctionId;

  @Column(name = "item_id", updatable = false)
  private UUID itemId;

  @Column(name = "sale_id", updatable = false)
  private UUID saleId;

  @Column(name = "reason_category", updatable = false, length = 64)
  private String reasonCategory;

  @Column(name = "public_reason", updatable = false, length = 1000)
  private String publicReason;

  @Column(name = "internal_note", updatable = false, length = 2000)
  private String internalNote;

  @Column(name = "amount_cents", updatable = false)
  private Long amountCents;

  @Column(name = "bidder_pseudonym", updatable = false, length = 32)
  private String bidderPseudonym;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "audit_record_participants",
      joinColumns = @JoinColumn(name = "audit_record_id"))
  @Column(name = "account_id", nullable = false)
  private Set<UUID> participantAccountIds = new LinkedHashSet<>();

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
    this.sourceFingerprint =
        fingerprint(actorType, actorId, action, targetType, targetId, occurredAt, metadata);
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

  static AuditRecord accountCollectibleAction(
      AuditAction action, UUID accountId, UUID itemId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.REGULAR_ACCOUNT,
        accountId,
        action,
        TargetType.COLLECTIBLE_ITEM,
        itemId,
        occurredAt,
        metadata);
  }

  static AuditRecord operationalCollectibleAction(
      AuditAction action, UUID actorId, UUID itemId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.OPERATIONAL_ACCOUNT,
        actorId,
        action,
        TargetType.COLLECTIBLE_ITEM,
        itemId,
        occurredAt,
        metadata);
  }

  static AuditRecord accountAuctionAction(
      AuditAction action, UUID accountId, UUID auctionId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.REGULAR_ACCOUNT,
        accountId,
        action,
        TargetType.AUCTION,
        auctionId,
        occurredAt,
        metadata);
  }

  static AuditRecord systemAuctionAction(
      AuditAction action, UUID auctionId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.SYSTEM, null, action, TargetType.AUCTION, auctionId, occurredAt, metadata);
  }

  static AuditRecord accountSaleAction(
      AuditAction action, UUID accountId, UUID saleId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.REGULAR_ACCOUNT,
        accountId,
        action,
        TargetType.SALE,
        saleId,
        occurredAt,
        metadata);
  }

  static AuditRecord systemSaleAction(
      AuditAction action, UUID saleId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.SYSTEM, null, action, TargetType.SALE, saleId, occurredAt, metadata);
  }

  static AuditRecord operationalAuctionAction(
      AuditAction action, UUID actorId, UUID auctionId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.OPERATIONAL_ACCOUNT,
        actorId,
        action,
        TargetType.AUCTION,
        auctionId,
        occurredAt,
        metadata);
  }

  static AuditRecord operationalAction(
      AuditAction action, UUID actorId, UUID targetId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.OPERATIONAL_ACCOUNT,
        actorId,
        action,
        TargetType.OPERATIONAL_ACCOUNT,
        targetId,
        occurredAt,
        metadata);
  }

  static AuditRecord operationalRegularAccountAction(
      AuditAction action, UUID actorId, UUID targetId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.OPERATIONAL_ACCOUNT,
        actorId,
        action,
        TargetType.REGULAR_ACCOUNT,
        targetId,
        occurredAt,
        metadata);
  }

  static AuditRecord operationalAcceptedBidAction(
      AuditAction action, UUID actorId, UUID targetId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.OPERATIONAL_ACCOUNT,
        actorId,
        action,
        TargetType.ACCEPTED_BID,
        targetId,
        occurredAt,
        metadata);
  }

  static AuditRecord systemOperationalAction(
      AuditAction action, UUID targetId, Instant occurredAt, String metadata) {
    return new AuditRecord(
        ActorType.SYSTEM,
        null,
        action,
        TargetType.OPERATIONAL_ACCOUNT,
        targetId,
        occurredAt,
        metadata);
  }

  UUID id() {
    return id;
  }

  ActorType actorType() {
    return actorType;
  }

  UUID actorId() {
    return actorId;
  }

  AuditAction action() {
    return action;
  }

  TargetType targetType() {
    return targetType;
  }

  UUID targetId() {
    return targetId;
  }

  Instant occurredAt() {
    return occurredAt;
  }

  String metadata() {
    return metadata;
  }

  String sourceFingerprint() {
    return sourceFingerprint;
  }

  UUID auctionId() {
    return auctionId;
  }

  UUID itemId() {
    return itemId;
  }

  UUID saleId() {
    return saleId;
  }

  String reasonCategory() {
    return reasonCategory;
  }

  String publicReason() {
    return publicReason;
  }

  String internalNote() {
    return internalNote;
  }

  Long amountCents() {
    return amountCents;
  }

  String bidderPseudonym() {
    return bidderPseudonym;
  }

  AuditRecord participates(UUID... accountIds) {
    for (UUID accountId : accountIds) {
      if (accountId != null) {
        participantAccountIds.add(accountId);
      }
    }
    return this;
  }

  AuditRecord forAuction(UUID value) {
    auctionId = value;
    return this;
  }

  AuditRecord forItem(UUID value) {
    itemId = value;
    return this;
  }

  AuditRecord forSale(UUID value) {
    saleId = value;
    return this;
  }

  AuditRecord withPublicDetails(
      String category, String reason, Long amount, String pseudonym, String note) {
    reasonCategory = category;
    publicReason = reason;
    amountCents = amount;
    bidderPseudonym = pseudonym;
    internalNote = note;
    return this;
  }

  private static String fingerprint(
      ActorType actorType,
      UUID actorId,
      AuditAction action,
      TargetType targetType,
      UUID targetId,
      Instant occurredAt,
      String metadata) {
    String source =
        actorType
            + "|"
            + actorId
            + "|"
            + action
            + "|"
            + targetType
            + "|"
            + targetId
            + "|"
            + occurredAt
            + "|"
            + metadata;
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        hexadecimal.append(String.format("%02x", value));
      }
      return hexadecimal.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
          "SHA-256 must be available to create audit records.", exception);
    }
  }

  enum ActorType {
    SYSTEM,
    REGULAR_ACCOUNT,
    OPERATIONAL_ACCOUNT
  }

  enum AuditAction {
    REGULAR_ACCOUNT_REGISTERED,
    REGULAR_ACCOUNT_VERIFIED,
    REGULAR_ACCOUNT_PASSWORD_RESET,
    REGULAR_ACCOUNT_SUSPENDED,
    REGULAR_ACCOUNT_REACTIVATED,
    INITIAL_ADMINISTRATOR_CREATED,
    OPERATIONAL_ACCOUNT_INVITED,
    OPERATIONAL_ACCOUNT_ACTIVATED,
    OPERATIONAL_ACCOUNT_DEACTIVATED,
    COLLECTIBLE_SUBMITTED,
    COLLECTIBLE_APPROVED,
    COLLECTIBLE_REJECTED,
    AUCTION_SCHEDULED,
    AUCTION_RESCHEDULED,
    AUCTION_STARTED,
    AUCTION_CANCELLED,
    AUCTION_ENDED,
    AUCTION_SOLD,
    AUCTION_UNSOLD,
    AUCTION_AWAITING_SELLER_DECISION,
    AUCTION_SELLER_DECISION_ACCEPTED,
    AUCTION_SELLER_DECISION_REJECTED,
    AUCTION_SELLER_DECISION_EXPIRED,
    AUCTION_SELLER_DECISION_REOPENED,
    AUCTION_SELLER_DECISION_NO_ELIGIBLE_BID,
    AUCTION_SUSPENDED,
    AUCTION_RELEASED,
    AUCTION_RESUMED,
    AUCTION_ADMINISTRATIVELY_CANCELLED,
    BID_ACCEPTED,
    BID_DISQUALIFIED,
    SALE_CREATED,
    SALE_PAYMENT_RECORDED,
    SALE_SHIPMENT_RECORDED,
    SALE_PAYMENT_EXPIRED,
    SALE_SHIPMENT_EXPIRED,
    SALE_DELIVERY_CONFIRMED,
    SALE_DELIVERY_CONFIRMATION_DEADLINE_EXPIRED
  }

  enum TargetType {
    REGULAR_ACCOUNT,
    OPERATIONAL_ACCOUNT,
    COLLECTIBLE_ITEM,
    AUCTION,
    ACCEPTED_BID,
    SALE
  }
}
