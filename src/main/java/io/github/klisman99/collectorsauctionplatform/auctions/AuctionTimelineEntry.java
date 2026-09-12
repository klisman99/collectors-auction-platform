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
  private String reasonCategory;

  @Column(name = "internal_note", updatable = false, length = 2000)
  private String internalNote;

  @Column(name = "item_disposition", updatable = false)
  private String itemDisposition;

  protected AuctionTimelineEntry() {}

  private AuctionTimelineEntry(
      Type type,
      Instant occurredAt,
      String reasonCategory,
      String publicReason,
      String internalNote,
      String itemDisposition) {
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

  static AuctionTimelineEntry suspended(
      Instant occurredAt, String reasonCategory, String publicReason, String internalNote) {
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
      String reasonCategory,
      String publicReason,
      String internalNote,
      String itemDisposition) {
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

  String reasonCategory() {
    return reasonCategory;
  }

  String internalNote() {
    return internalNote;
  }

  String itemDisposition() {
    return itemDisposition;
  }

  enum Type {
    SCHEDULED,
    RESCHEDULED,
    STARTED,
    CANCELLED,
    ENDED,
    SUSPENDED,
    RELEASED,
    RESUMED
  }
}
