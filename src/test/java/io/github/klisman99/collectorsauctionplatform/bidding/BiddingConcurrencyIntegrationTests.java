package io.github.klisman99.collectorsauctionplatform.bidding;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
    properties = {
      "platform.identity.initial-administrator.email=bidding-lock@example.com",
      "platform.identity.initial-administrator.password=bidding lock administrator password"
    })
class BiddingConcurrencyIntegrationTests {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:18.6-alpine")
          .withDatabaseName("collectors_auction")
          .withUsername("collectors")
          .withPassword("collectors");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private BiddingService bidding;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearFixtures() {
    jdbcTemplate.update("DELETE FROM bid_attempts");
    jdbcTemplate.update("DELETE FROM accepted_bids");
    jdbcTemplate.update("DELETE FROM bidder_pseudonyms");
    jdbcTemplate.update("DELETE FROM auction_timeline_events");
    jdbcTemplate.update("DELETE FROM auction_item_snapshot_media");
    jdbcTemplate.update("DELETE FROM auctions");
    jdbcTemplate.update("DELETE FROM collectible_items");
    jdbcTemplate.update("DELETE FROM regular_accounts");
  }

  @Test
  void acceptsOpeningBidDurablyAndDeduplicatesTheSameCommand() {
    UUID sellerId = activeAccount("seller");
    UUID bidderId = activeAccount("bidder");
    UUID auctionId = liveAuction(sellerId, 10_000, 1_000);
    UUID idempotencyKey = UUID.randomUUID();

    BidCommandResult accepted = bidding.place(bidderId, auctionId, idempotencyKey, 10_000);
    BidCommandResult replayed = bidding.place(bidderId, auctionId, idempotencyKey, 10_000);

    assertThat(accepted.status()).isEqualTo(BidCommandResult.Status.ACCEPTED);
    assertThat(accepted.sequence()).isEqualTo(1);
    assertThat(replayed.status()).isEqualTo(BidCommandResult.Status.DEDUPLICATED);
    assertThat(replayed.sequence()).isEqualTo(accepted.sequence());
    assertThat(replayed.acceptedAt()).isEqualTo(accepted.acceptedAt());
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM accepted_bids", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM bid_attempts", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT current_amount_cents FROM auctions WHERE id = ?", Long.class, auctionId))
        .isEqualTo(10_000L);
  }

  @Test
  void serializesEqualCompetingBidsIntoOneDurableWinner() throws Exception {
    UUID sellerId = activeAccount("seller");
    UUID firstBidder = activeAccount("first");
    UUID secondBidder = activeAccount("second");
    UUID auctionId = liveAuction(sellerId, 10_000, 1_000);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<BidCommandResult> first =
          executor.submit(() -> placeAfter(start, firstBidder, auctionId, 10_000));
      Future<BidCommandResult> second =
          executor.submit(() -> placeAfter(start, secondBidder, auctionId, 10_000));
      start.countDown();

      assertThat(
              java.util.List.of(
                  first.get(20, TimeUnit.SECONDS).status(),
                  second.get(20, TimeUnit.SECONDS).status()))
          .containsExactlyInAnyOrder(
              BidCommandResult.Status.ACCEPTED, BidCommandResult.Status.REJECTED);
    }

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM accepted_bids", Integer.class))
        .isEqualTo(1);
    assertThat(bidding.history(auctionId))
        .singleElement()
        .satisfies(
            bid -> {
              assertThat(bid.amountCents()).isEqualTo(10_000);
              assertThat(bid.sequence()).isEqualTo(1);
              assertThat(bid.bidderPseudonym()).startsWith("Bidder-");
            });
  }

  @Test
  void enforcesIncrementSellerEligibilityIdempotencyPayloadAndRateLimit() {
    UUID sellerId = activeAccount("seller");
    UUID bidderId = activeAccount("bidder");
    UUID unverifiedBidderId = account("unverified", "ACTIVE", null);
    UUID suspendedBidderId = account("suspended", "SUSPENDED", Instant.now());
    UUID auctionId = liveAuction(sellerId, 10_000, 1_000);

    assertThat(bidding.place(sellerId, auctionId, UUID.randomUUID(), 10_000).code())
        .isEqualTo("BIDDER_INELIGIBLE");
    assertThat(bidding.place(unverifiedBidderId, auctionId, UUID.randomUUID(), 10_000).code())
        .isEqualTo("BIDDER_UNVERIFIED");
    assertThat(bidding.place(suspendedBidderId, auctionId, UUID.randomUUID(), 10_000).code())
        .isEqualTo("BIDDER_INACTIVE");

    UUID acceptedKey = UUID.randomUUID();
    BidCommandResult opening = bidding.place(bidderId, auctionId, acceptedKey, 10_000);
    assertThat(opening.status()).isEqualTo(BidCommandResult.Status.ACCEPTED);
    BidCommandResult ownRaise = bidding.place(bidderId, auctionId, UUID.randomUUID(), 11_000);
    assertThat(ownRaise.status()).isEqualTo(BidCommandResult.Status.ACCEPTED);
    assertThat(ownRaise.sequence()).isEqualTo(2);
    assertThat(ownRaise.bidderPseudonym()).isEqualTo(opening.bidderPseudonym());
    assertThat(bidding.place(bidderId, auctionId, acceptedKey, 11_000).status())
        .isEqualTo(BidCommandResult.Status.IDEMPOTENCY_CONFLICT);

    UUID rejectedKey = UUID.randomUUID();
    BidCommandResult tooLow = bidding.place(bidderId, auctionId, rejectedKey, 11_999);
    assertThat(tooLow.code()).isEqualTo("BID_AMOUNT_TOO_LOW");
    assertThat(tooLow.requiredAmountCents()).isEqualTo(12_000);
    BidCommandResult rejectedReplay = bidding.place(bidderId, auctionId, rejectedKey, 11_999);
    assertThat(rejectedReplay).isEqualTo(tooLow);

    for (int index = 0; index < 6; index++) {
      bidding.place(bidderId, auctionId, UUID.randomUUID(), 11_999);
    }
    assertThat(bidding.place(bidderId, auctionId, UUID.randomUUID(), 11_999).status())
        .isEqualTo(BidCommandResult.Status.RATE_LIMITED);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM accepted_bids", Integer.class))
        .isEqualTo(2);

    jdbcTemplate.update(
        "UPDATE auctions SET ends_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minusSeconds(1)),
        auctionId);
    assertThat(bidding.place(activeAccount("late"), auctionId, UUID.randomUUID(), 12_000).code())
        .isEqualTo("BID_LATE_OR_UNAVAILABLE");
  }

  private BidCommandResult placeAfter(
      CountDownLatch start, UUID bidderId, UUID auctionId, long amountCents) {
    try {
      start.await(10, TimeUnit.SECONDS);
      return bidding.place(bidderId, auctionId, UUID.randomUUID(), amountCents);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private UUID activeAccount(String prefix) {
    return account(prefix, "ACTIVE", Instant.now());
  }

  private UUID account(String prefix, String status, Instant verifiedAt) {
    UUID accountId = UUID.randomUUID();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
            (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, 'unused', ?, ?, ?)
        """,
        accountId,
        accountId + "@example.com",
        prefix + "_" + accountId.toString().substring(0, 8),
        status,
        Timestamp.from(now),
        verifiedAt == null ? null : Timestamp.from(verifiedAt));
    return accountId;
  }

  private UUID liveAuction(UUID sellerId, long openingAmountCents, long incrementCents) {
    UUID itemId = UUID.randomUUID();
    UUID auctionId = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    jdbcTemplate.update(
        """
        INSERT INTO collectible_items
            (id, owner_id, category, title, description, condition, condition_notes,
             ownership_declared, status, created_at, updated_at, auction_locked_at)
        VALUES (?, ?, 'CARDS', 'Bid fixture', 'A durable bidding fixture.', 'EXCELLENT',
                'No visible wear.', TRUE, 'APPROVED', ?, ?, ?)
        """,
        itemId,
        sellerId,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
    jdbcTemplate.update(
        """
        INSERT INTO auctions
            (id, item_id, active_item_id, seller_id, seller_handle, state,
             opening_amount_cents, current_amount_cents, minimum_increment_cents,
             starts_at, ends_at, scheduled_at, snapshot_category, snapshot_title,
             snapshot_description, snapshot_condition, snapshot_condition_notes,
             snapshot_ownership_declared, policy_auction_type, policy_currency,
             policy_minimum_amount_cents, policy_maximum_amount_cents,
             policy_minimum_lead_seconds, policy_minimum_duration_seconds,
             policy_maximum_duration_seconds, policy_protection_window_seconds)
        VALUES (?, ?, ?, ?, 'fixture_seller', 'LIVE', ?, ?, ?, ?, ?, ?, 'CARDS',
                'Bid fixture', 'A durable bidding fixture.', 'EXCELLENT', 'No visible wear.',
                TRUE, 'ENGLISH_ASCENDING', 'BRL', 1000, 100000000, 300, 600, 604800, 120)
        """,
        auctionId,
        itemId,
        itemId,
        sellerId,
        openingAmountCents,
        openingAmountCents,
        incrementCents,
        Timestamp.from(now.minusSeconds(60)),
        Timestamp.from(now.plusSeconds(3600)),
        Timestamp.from(now.minusSeconds(120)));
    return auctionId;
  }
}
