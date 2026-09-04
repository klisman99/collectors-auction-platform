package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable fact published after a regular account consumes a valid verification token.
 */
public record RegularAccountVerified(UUID accountId, Instant occurredAt) {
}
