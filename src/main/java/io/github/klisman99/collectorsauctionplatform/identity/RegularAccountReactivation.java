package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/** Immutable administrative command that records why a regular account regained trading access. */
public record RegularAccountReactivation(
    UUID actorId,
    RegularAccountSuspensionReasonCategory reasonCategory,
    String publicReason,
    String internalNote,
    Instant occurredAt) {}
