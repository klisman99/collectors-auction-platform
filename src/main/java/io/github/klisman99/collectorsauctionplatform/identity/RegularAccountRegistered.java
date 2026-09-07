package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/** Durable fact published when a visitor has registered a regular account. */
public record RegularAccountRegistered(
    UUID accountId,
    String email,
    String publicHandle,
    String verificationToken,
    Instant occurredAt) {}
