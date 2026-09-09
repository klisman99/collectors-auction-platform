package io.github.klisman99.collectorsauctionplatform.moderation;

import java.time.Instant;
import java.util.UUID;

/** Durable fact published after an operational account records a moderation decision. */
public record ModerationDecisionMade(
    UUID itemId,
    UUID ownerId,
    UUID reviewerId,
    String ownerEmail,
    String title,
    Decision decision,
    String publicReason,
    Instant occurredAt) {
  public enum Decision {
    APPROVED,
    REJECTED
  }
}
