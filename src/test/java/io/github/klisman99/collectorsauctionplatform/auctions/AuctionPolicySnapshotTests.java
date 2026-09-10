package io.github.klisman99.collectorsauctionplatform.auctions;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionPolicySnapshotTests {

  private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

  @Test
  void acceptsInclusiveMoneyLeadAndDurationBoundaries() {
    AuctionPolicySnapshot policy = AuctionPolicySnapshot.current();
    Instant earliestStart = NOW.plus(AuctionPolicySnapshot.MINIMUM_LEAD_TIME);

    assertThatCode(
            () ->
                policy.validateTerms(
                    AuctionPolicySnapshot.MINIMUM_AMOUNT_CENTS,
                    AuctionPolicySnapshot.MAXIMUM_AMOUNT_CENTS,
                    AuctionPolicySnapshot.MAXIMUM_AMOUNT_CENTS,
                    earliestStart,
                    earliestStart.plus(AuctionPolicySnapshot.MINIMUM_DURATION),
                    NOW))
        .doesNotThrowAnyException();

    assertThatCode(
            () ->
                policy.validateTerms(
                    AuctionPolicySnapshot.MAXIMUM_AMOUNT_CENTS,
                    AuctionPolicySnapshot.MINIMUM_AMOUNT_CENTS,
                    null,
                    earliestStart,
                    earliestStart.plus(AuctionPolicySnapshot.MAXIMUM_DURATION),
                    NOW))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAmountsOutsideThePublishedCentLimits() {
    Instant startsAt = NOW.plus(AuctionPolicySnapshot.MINIMUM_LEAD_TIME);

    assertInvalid(
        "BR-AUC-002",
        () ->
            AuctionPolicySnapshot.current()
                .validateTerms(
                    AuctionPolicySnapshot.MINIMUM_AMOUNT_CENTS - 1,
                    AuctionPolicySnapshot.MINIMUM_AMOUNT_CENTS,
                    null,
                    startsAt,
                    startsAt.plus(AuctionPolicySnapshot.MINIMUM_DURATION),
                    NOW));
    assertInvalid(
        "BR-AUC-002",
        () ->
            AuctionPolicySnapshot.current()
                .validateTerms(
                    AuctionPolicySnapshot.MINIMUM_AMOUNT_CENTS,
                    AuctionPolicySnapshot.MAXIMUM_AMOUNT_CENTS + 1,
                    null,
                    startsAt,
                    startsAt.plus(AuctionPolicySnapshot.MINIMUM_DURATION),
                    NOW));
  }

  @Test
  void rejectsLeadAndDurationImmediatelyOutsidePublishedBoundaries() {
    AuctionPolicySnapshot policy = AuctionPolicySnapshot.current();
    Instant earliestStart = NOW.plus(AuctionPolicySnapshot.MINIMUM_LEAD_TIME);

    assertInvalid(
        "BR-AUC-005",
        () ->
            policy.validateTerms(
                1_000,
                1_000,
                null,
                earliestStart.minusSeconds(1),
                earliestStart.plus(AuctionPolicySnapshot.MINIMUM_DURATION),
                NOW));
    assertInvalid(
        "BR-AUC-005",
        () ->
            policy.validateTerms(
                1_000,
                1_000,
                null,
                earliestStart,
                earliestStart.plus(AuctionPolicySnapshot.MINIMUM_DURATION).minusSeconds(1),
                NOW));
    assertInvalid(
        "BR-AUC-005",
        () ->
            policy.validateTerms(
                1_000,
                1_000,
                null,
                earliestStart,
                earliestStart.plus(AuctionPolicySnapshot.MAXIMUM_DURATION).plusSeconds(1),
                NOW));
  }

  @Test
  void validatesEditsAgainstTheFrozenPolicyValues() {
    AuctionPolicySnapshot frozenPolicy = AuctionPolicySnapshot.current();
    ReflectionTestUtils.setField(
        frozenPolicy, "minimumLeadSeconds", Duration.ofHours(1).toSeconds());
    Instant startsAt = NOW.plus(Duration.ofMinutes(30));

    assertInvalid(
        "BR-AUC-005",
        () ->
            frozenPolicy.validateTerms(
                1_000, 1_000, null, startsAt, startsAt.plusSeconds(600), NOW));
  }

  private void assertInvalid(String ruleId, Runnable validation) {
    assertThatThrownBy(validation::run)
        .isInstanceOf(AuctionApiException.class)
        .satisfies(
            exception ->
                org.assertj.core.api.Assertions.assertThat(
                        ((AuctionApiException) exception).getBody().getProperties().get("ruleId"))
                    .isEqualTo(ruleId));
  }
}
