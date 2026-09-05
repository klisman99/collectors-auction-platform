package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable fact published after a regular account password has been changed.
 */
public record RegularAccountPasswordReset(UUID accountId, Instant occurredAt) {
}
