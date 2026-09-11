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

  protected AuctionTimelineEntry() {}

  private AuctionTimelineEntry(Type type, Instant occurredAt, String publicReason) {
    this.type = type;
    this.occurredAt = occurredAt;
    this.publicReason = publicReason;
  }

  static AuctionTimelineEntry scheduled(Instant occurredAt) {
    return new AuctionTimelineEntry(Type.SCHEDULED, occurredAt, null);
  }

  static AuctionTimelineEntry rescheduled(Instant occurredAt) {
    return new AuctionTimelineEntry(Type.RESCHEDULED, occurredAt, null);
  }

  static AuctionTimelineEntry cancelled(Instant occurredAt, String publicReason) {
    return new AuctionTimelineEntry(Type.CANCELLED, occurredAt, publicReason);
  }

  static AuctionTimelineEntry started(Instant occurredAt) {
    return new AuctionTimelineEntry(Type.STARTED, occurredAt, null);
  }

  static AuctionTimelineEntry ended(Instant occurredAt) {
    return new AuctionTimelineEntry(Type.ENDED, occurredAt, null);
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

  enum Type {
    SCHEDULED,
    RESCHEDULED,
    STARTED,
    CANCELLED,
    ENDED
  }
}
