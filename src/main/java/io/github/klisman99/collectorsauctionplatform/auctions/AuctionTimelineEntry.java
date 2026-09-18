package io.github.klisman99.collectorsauctionplatform.auctions;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

@Embeddable
class AuctionTimelineEntry {

  @Column(name = "event_type", nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  private Type type;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "public_reason", updatable = false)
  private String publicReason;

  @Column(name = "reason_category", updatable = false)
  @Enumerated(EnumType.STRING)
  private SuspensionReasonCategory reasonCategory;

  @Column(name = "internal_note", updatable = false, length = 2000)
  private String internalNote;

  @Column(name = "item_disposition", updatable = false)
  @Enumerated(EnumType.STRING)
  private AdministrativeItemDisposition itemDisposition;

  protected AuctionTimelineEntry() {}

  private AuctionTimelineEntry(
      Type type,
      Instant occurredAt,
      SuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote,
      AdministrativeItemDisposition itemDisposition) {
    this.type = type;
    this.occurredAt = occurredAt;
    this.reasonCategory = reasonCategory;
    this.publicReason = publicReason;
    this.internalNote = internalNote;
    this.itemDisposition = itemDisposition;
  }

  static AuctionTimelineEntry scheduled(Instant occurredAt) {
    return simple(Type.SCHEDULED, occurredAt);
  }

  static AuctionTimelineEntry rescheduled(Instant occurredAt) {
    return simple(Type.RESCHEDULED, occurredAt);
  }

  static AuctionTimelineEntry cancelled(Instant occurredAt, String publicReason) {
    return new AuctionTimelineEntry(Type.CANCELLED, occurredAt, null, publicReason, null, null);
  }

  static AuctionTimelineEntry started(Instant occurredAt) {
    return simple(Type.STARTED, occurredAt);
  }

  static AuctionTimelineEntry ended(Instant occurredAt) {
    return simple(Type.ENDED, occurredAt);
  }

  static AuctionTimelineEntry awaitingSellerDecision(Instant occurredAt) {
    return simple(Type.AWAITING_SELLER_DECISION, occurredAt);
  }

  static AuctionTimelineEntry sellerDecisionAccepted(Instant occurredAt) {
    return simple(Type.SELLER_DECISION_ACCEPTED, occurredAt);
  }

  static AuctionTimelineEntry sellerDecisionReopened(Instant occurredAt) {
    return simple(Type.SELLER_DECISION_REOPENED, occurredAt);
  }

  static AuctionTimelineEntry of(Type type, Instant occurredAt) {
    return simple(type, occurredAt);
  }

  static AuctionTimelineEntry suspended(
      Instant occurredAt,
      SuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote) {
    return new AuctionTimelineEntry(
        Type.SUSPENDED, occurredAt, reasonCategory, publicReason, internalNote, null);
  }

  static AuctionTimelineEntry released(Instant occurredAt) {
    return simple(Type.RELEASED, occurredAt);
  }

  static AuctionTimelineEntry resumed(Instant occurredAt) {
    return simple(Type.RESUMED, occurredAt);
  }

  static AuctionTimelineEntry administrativelyCancelled(
      Instant occurredAt,
      SuspensionReasonCategory reasonCategory,
      String publicReason,
      String internalNote,
      AdministrativeItemDisposition itemDisposition) {
    return new AuctionTimelineEntry(
        Type.CANCELLED, occurredAt, reasonCategory, publicReason, internalNote, itemDisposition);
  }

  private static AuctionTimelineEntry simple(Type type, Instant occurredAt) {
    return new AuctionTimelineEntry(type, occurredAt, null, null, null, null);
  }

  Type type() {
    return type;
  }

  Instant occurredAt() {
    return occurredAt;
  }

  String publicReason() {
    return publicReason;
  }

  SuspensionReasonCategory reasonCategory() {
    return reasonCategory;
  }

  String internalNote() {
    return internalNote;
  }

  AdministrativeItemDisposition itemDisposition() {
    return itemDisposition;
  }

  enum Type {
    SCHEDULED,
    RESCHEDULED,
    STARTED,
    CANCELLED,
    ENDED,
    AWAITING_SELLER_DECISION,
    SELLER_DECISION_ACCEPTED,
    SELLER_DECISION_REJECTED,
    SELLER_DECISION_EXPIRED,
    SELLER_DECISION_REOPENED,
    SELLER_DECISION_NO_ELIGIBLE_BID,
    SUSPENDED,
    RELEASED,
    RESUMED
  }
}
