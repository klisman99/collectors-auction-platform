package io.github.klisman99.collectorsauctionplatform.catalog;

import java.time.Instant;
import java.util.UUID;

/** Durable fact published after a seller submits a collectible for moderation. */
public record CollectibleSubmitted(UUID itemId, UUID ownerId, String title, Instant occurredAt) {}
