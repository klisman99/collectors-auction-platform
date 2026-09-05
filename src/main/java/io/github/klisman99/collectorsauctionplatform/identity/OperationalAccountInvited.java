package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable fact published after an administrator creates an operational-account invitation.
 */
public record OperationalAccountInvited(
        UUID accountId,
        UUID actorId,
        String email,
        String role,
        String activationToken,
        Instant expiresAt,
        String reasonCategory,
        String publicReason,
        String internalNote,
        Instant occurredAt) {
}
