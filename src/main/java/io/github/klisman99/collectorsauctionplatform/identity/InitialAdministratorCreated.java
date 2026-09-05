package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable fact published when the configured bootstrap administrator is created.
 */
public record InitialAdministratorCreated(UUID accountId, String email, Instant occurredAt) {
}
