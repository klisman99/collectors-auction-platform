package io.github.klisman99.collectorsauctionplatform.auctions;

import java.time.Instant;
import java.util.UUID;

record ScheduleAuction(
    UUID itemId,
    long openingAmountCents,
    long minimumIncrementCents,
    Long reserveAmountCents,
    Instant startsAt,
    Instant endsAt) {}
