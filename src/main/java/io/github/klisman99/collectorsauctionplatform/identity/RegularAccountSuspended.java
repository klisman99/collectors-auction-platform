package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/** Durable fact published when an administrator suspends a regular account. */
public record RegularAccountSuspended(
    UUID accountId,
    UUID actorId,
    String publicHandle,
    String reasonCategory,
    String publicReason,
    String internalNote,
    Instant occurredAt) {}
