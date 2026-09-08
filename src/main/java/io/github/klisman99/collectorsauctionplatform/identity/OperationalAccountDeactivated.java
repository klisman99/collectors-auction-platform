package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/** Durable fact published after an administrator deactivates an operational account. */
public record OperationalAccountDeactivated(
    UUID accountId,
    UUID actorId,
    String role,
    String reasonCategory,
    String publicReason,
    String internalNote,
    Instant occurredAt) {}
