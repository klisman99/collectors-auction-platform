package io.github.klisman99.collectorsauctionplatform.auctions;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Duration;
import java.time.Instant;

@Embeddable
class AuctionPolicySnapshot {

  static final long MINIMUM_AMOUNT_CENTS = 1_000;
  static final long MAXIMUM_AMOUNT_CENTS = 100_000_000;
  static final Duration MINIMUM_LEAD_TIME = Duration.ofMinutes(5);
  static final Duration MINIMUM_DURATION = Duration.ofMinutes(10);
  static final Duration MAXIMUM_DURATION = Duration.ofDays(7);
  static final Duration PROTECTION_WINDOW = Duration.ofMinutes(2);

  @Column(name = "policy_auction_type", nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  private AuctionType auctionType;

  @Column(name = "policy_currency", nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  private Currency currency;

  @Column(name = "policy_minimum_amount_cents", nullable = false, updatable = false)
  private long minimumAmountCents;

  @Column(name = "policy_maximum_amount_cents", nullable = false, updatable = false)
  private long maximumAmountCents;

  @Column(name = "policy_minimum_lead_seconds", nullable = false, updatable = false)
  private long minimumLeadSeconds;

  @Column(name = "policy_minimum_duration_seconds", nullable = false, updatable = false)
  private long minimumDurationSeconds;

  @Column(name = "policy_maximum_duration_seconds", nullable = false, updatable = false)
  private long maximumDurationSeconds;

  @Column(name = "policy_protection_window_seconds", nullable = false, updatable = false)
  private long protectionWindowSeconds;

  protected AuctionPolicySnapshot() {}

  static AuctionPolicySnapshot current() {
    AuctionPolicySnapshot policy = new AuctionPolicySnapshot();
    policy.auctionType = AuctionType.ENGLISH_ASCENDING;
    policy.currency = Currency.BRL;
    policy.minimumAmountCents = MINIMUM_AMOUNT_CENTS;
    policy.maximumAmountCents = MAXIMUM_AMOUNT_CENTS;
    policy.minimumLeadSeconds = MINIMUM_LEAD_TIME.toSeconds();
    policy.minimumDurationSeconds = MINIMUM_DURATION.toSeconds();
    policy.maximumDurationSeconds = MAXIMUM_DURATION.toSeconds();
    policy.protectionWindowSeconds = PROTECTION_WINDOW.toSeconds();
    return policy;
  }

  void validateTerms(
      long openingAmountCents,
      long minimumIncrementCents,
      Long reserveAmountCents,
      Instant startsAt,
      Instant endsAt,
      Instant now) {
    validateAmount("openingAmountCents", openingAmountCents);
    validateAmount("minimumIncrementCents", minimumIncrementCents);
    if (reserveAmountCents != null) {
      validateAmount("reserveAmountCents", reserveAmountCents);
      if (reserveAmountCents < openingAmountCents) {
        throw AuctionApiException.invalidTerms(
            "BR-AUC-003", "Reserve cannot be below the opening amount.");
      }
    }
    if (startsAt == null || endsAt == null) {
      throw AuctionApiException.invalidTerms(
          "BR-AUC-005", "Start and end must be authoritative UTC instants.");
    }
    if (startsAt.isBefore(now.plusSeconds(minimumLeadSeconds))) {
      throw AuctionApiException.invalidTerms(
          "BR-AUC-005", "Start must be at least five minutes after scheduling.");
    }
    Duration duration = Duration.between(startsAt, endsAt);
    if (duration.compareTo(Duration.ofSeconds(minimumDurationSeconds)) < 0
        || duration.compareTo(Duration.ofSeconds(maximumDurationSeconds)) > 0) {
      throw AuctionApiException.invalidTerms(
          "BR-AUC-005", "Duration must be between ten minutes and seven days.");
    }
  }

  private void validateAmount(String field, long amountCents) {
    if (amountCents < minimumAmountCents || amountCents > maximumAmountCents) {
      throw AuctionApiException.invalidTerms(
          "BR-AUC-002", field + " must be between R$ 10.00 and R$ 1,000,000.00 in whole cents.");
    }
  }

  AuctionType auctionType() {
    return auctionType;
  }

  Currency currency() {
    return currency;
  }

  long protectionWindowSeconds() {
    return protectionWindowSeconds;
  }

  long minimumAmountCents() {
    return minimumAmountCents;
  }

  long maximumAmountCents() {
    return maximumAmountCents;
  }

  long minimumLeadSeconds() {
    return minimumLeadSeconds;
  }

  long minimumDurationSeconds() {
    return minimumDurationSeconds;
  }

  long maximumDurationSeconds() {
    return maximumDurationSeconds;
  }

  enum AuctionType {
    ENGLISH_ASCENDING
  }

  enum Currency {
    BRL
  }
}
