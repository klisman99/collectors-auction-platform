package io.github.klisman99.collectorsauctionplatform.notifications;

import java.time.Instant;
import java.util.UUID;

record AuctionRealtimeEvent(
    UUID auctionId,
    String type,
    long projectionVersion,
    Long bidSequence,
    Long amountCents,
    String bidderPseudonym,
    Instant occurredAt,
    Long currentAmountCents,
    Long nextMinimumAmountCents,
    Boolean reserveMet,
    Instant effectiveEndAt) {}
