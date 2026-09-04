package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable fact published when an existing account requests password recovery.
 */
public record PasswordRecoveryRequested(
        UUID accountId,
        String email,
        String publicHandle,
        String recoveryToken,
        Instant occurredAt) {
}
