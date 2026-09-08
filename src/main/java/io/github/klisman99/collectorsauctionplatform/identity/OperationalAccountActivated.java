package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

/** Durable fact published after an invitee consumes an operational-account invitation. */
public record OperationalAccountActivated(UUID accountId, Instant occurredAt) {}
