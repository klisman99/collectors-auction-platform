package io.github.klisman99.collectorsauctionplatform.bidding;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.klisman99.collectorsauctionplatform.accountadministration.AccountAdministrationService;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountSuspensionReasonCategory;
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
import org.springframework.transaction.support.TransactionTemplate;
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

  @Autowired private AccountAdministrationService accountAdministration;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private TransactionTemplate transactions;

  @BeforeEach
  void clearFixtures() {
    jdbcTemplate.update("DELETE FROM bid_attempts");
    jdbcTemplate.update("DELETE FROM bid_disqualifications");
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
  void persistsTheWinningProtectedBidDeadlineUnderPostgreSqlContention() throws Exception {
    UUID sellerId = activeAccount("seller");
    UUID firstBidder = activeAccount("first");
    UUID secondBidder = activeAccount("second");
    UUID auctionId = liveAuction(sellerId, 10_000, 1_000);
    Instant originalEnd = Instant.now().plusSeconds(90).truncatedTo(ChronoUnit.MICROS);
    jdbcTemplate.update(
        "UPDATE auctions SET ends_at = ? WHERE id = ?", Timestamp.from(originalEnd), auctionId);
    CountDownLatch start = new CountDownLatch(1);

    BidCommandResult first;
    BidCommandResult second;
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<BidCommandResult> firstResult =
          executor.submit(() -> placeAfter(start, firstBidder, auctionId, 10_000));
      Future<BidCommandResult> secondResult =
          executor.submit(() -> placeAfter(start, secondBidder, auctionId, 10_000));
      start.countDown();
      first = firstResult.get(20, TimeUnit.SECONDS);
      second = secondResult.get(20, TimeUnit.SECONDS);
    }

    BidCommandResult accepted =
        java.util.List.of(first, second).stream()
            .filter(result -> result.status() == BidCommandResult.Status.ACCEPTED)
            .findFirst()
            .orElseThrow();
    assertThat(java.util.List.of(first.status(), second.status()))
        .containsExactlyInAnyOrder(
            BidCommandResult.Status.ACCEPTED, BidCommandResult.Status.REJECTED);
    assertThat(
            jdbcTemplate
                .queryForObject(
                    "SELECT ends_at FROM auctions WHERE id = ?", Timestamp.class, auctionId)
                .toInstant())
        .isEqualTo(accepted.acceptedAt().plusSeconds(120));
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM accepted_bids", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void serializesSuspensionWithABidAcceptedAtTheSameTime() throws Exception {
    UUID sellerId = activeAccount("seller");
    UUID bidderId = activeAccount("suspended_bidder");
    UUID auctionId = liveAuction(sellerId, 10_000, 1_000);
    CountDownLatch acceptedBidsTableLocked = new CountDownLatch(1);
    CountDownLatch releaseAcceptedBidsTable = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(3)) {
      Future<?> tableLock =
          executor.submit(
              () -> lockAcceptedBidsTable(acceptedBidsTableLocked, releaseAcceptedBidsTable));
      assertThat(acceptedBidsTableLocked.await(10, TimeUnit.SECONDS)).isTrue();

      Future<BidCommandResult> bid =
          executor.submit(() -> bidding.place(bidderId, auctionId, UUID.randomUUID(), 10_000));
      awaitPostgreSqlLockOn("accepted_bids");

      Future<?> suspension =
          executor.submit(
              () ->
                  accountAdministration.suspend(
                      UUID.randomUUID(),
                      bidderId,
                      RegularAccountSuspensionReasonCategory.FRAUD,
                      "The account is under review.",
                      "Concurrency coverage."));
      awaitPostgreSqlLockOn("regular_accounts");

      releaseAcceptedBidsTable.countDown();
      tableLock.get(10, TimeUnit.SECONDS);
      assertThat(bid.get(10, TimeUnit.SECONDS).status())
          .isEqualTo(BidCommandResult.Status.ACCEPTED);
      suspension.get(10, TimeUnit.SECONDS);
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM accepted_bids WHERE auction_id = ?",
                Integer.class,
                auctionId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bid_disqualifications WHERE accepted_bid_id = "
                    + "(SELECT id FROM accepted_bids WHERE auction_id = ?)",
                Integer.class,
                auctionId))
        .isEqualTo(1);
  }

  @Test
  void suspendsAccountsWithCrossAuctionBidsWithoutDeadlocking() throws Exception {
    UUID firstAccountId = activeAccount("first_seller");
    UUID secondAccountId = activeAccount("second_seller");
    UUID firstAuctionId = liveAuction(firstAccountId, 10_000, 1_000);
    UUID secondAuctionId = liveAuction(secondAccountId, 10_000, 1_000);
    bidding.place(secondAccountId, firstAuctionId, UUID.randomUUID(), 10_000);
    bidding.place(firstAccountId, secondAuctionId, UUID.randomUUID(), 10_000);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch advisoryLockAcquired = new CountDownLatch(1);
    CountDownLatch releaseAdvisoryLock = new CountDownLatch(1);

    installSuspensionTimelineLockTrigger();
    try (var executor = Executors.newFixedThreadPool(3)) {
      Future<?> advisoryLock =
          executor.submit(
              () -> holdTransactionAdvisoryLock(advisoryLockAcquired, releaseAdvisoryLock));
      assertThat(advisoryLockAcquired.await(10, TimeUnit.SECONDS)).isTrue();

      Future<?> firstSuspension = executor.submit(() -> suspendAfter(start, firstAccountId));
      Future<?> secondSuspension = executor.submit(() -> suspendAfter(start, secondAccountId));
      start.countDown();
      awaitPostgreSqlLockWaiters(2);
      releaseAdvisoryLock.countDown();

      advisoryLock.get(10, TimeUnit.SECONDS);
      firstSuspension.get(20, TimeUnit.SECONDS);
      secondSuspension.get(20, TimeUnit.SECONDS);
    } finally {
      releaseAdvisoryLock.countDown();
      removeSuspensionTimelineLockTrigger();
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM regular_accounts WHERE status = 'SUSPENDED'", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auctions WHERE state = 'SUSPENDED'", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bid_disqualifications", Integer.class))
        .isEqualTo(2);
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

  private void suspendAfter(CountDownLatch start, UUID accountId) {
    await(start);
    accountAdministration.suspend(
        UUID.randomUUID(),
        accountId,
        RegularAccountSuspensionReasonCategory.FRAUD,
        "The account is under review.",
        "Cross-auction concurrency coverage.");
  }

  private void lockAcceptedBidsTable(CountDownLatch acquired, CountDownLatch release) {
    transactions.executeWithoutResult(
        ignored -> {
          jdbcTemplate.execute("LOCK TABLE accepted_bids IN SHARE MODE");
          acquired.countDown();
          await(release);
        });
  }

  private void installSuspensionTimelineLockTrigger() {
    jdbcTemplate.execute(
        """
        CREATE FUNCTION block_suspension_timeline_event() RETURNS trigger
        LANGUAGE plpgsql
        AS $$
        BEGIN
          PERFORM pg_advisory_xact_lock(3737001);
          RETURN NEW;
        END;
        $$
        """);
    jdbcTemplate.execute(
        """
        CREATE TRIGGER block_suspension_timeline_event
        BEFORE INSERT ON auction_timeline_events
        FOR EACH ROW EXECUTE FUNCTION block_suspension_timeline_event()
        """);
  }

  private void removeSuspensionTimelineLockTrigger() {
    jdbcTemplate.execute(
        "DROP TRIGGER IF EXISTS block_suspension_timeline_event ON auction_timeline_events");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS block_suspension_timeline_event()");
  }

  private void holdTransactionAdvisoryLock(CountDownLatch acquired, CountDownLatch release) {
    transactions.executeWithoutResult(
        ignored -> {
          jdbcTemplate.execute("SELECT pg_advisory_xact_lock(3737001)");
          acquired.countDown();
          await(release);
        });
  }

  private void awaitPostgreSqlLockWaiters(int expectedWaiters) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Integer count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'",
              Integer.class);
      if (count != null && count >= expectedWaiters) {
        return;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("Expected " + expectedWaiters + " PostgreSQL lock waiters.");
  }

  private void awaitPostgreSqlLockOn(String tableName) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Integer count =
          jdbcTemplate.queryForObject(
              """
              SELECT count(*)
              FROM pg_stat_activity
              WHERE pid <> pg_backend_pid()
                AND wait_event_type = 'Lock'
                AND query LIKE ?
              """,
              Integer.class,
              "%" + tableName + "%");
      if (count != null && count > 0) {
        return;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("Expected a PostgreSQL lock wait on " + tableName + ".");
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out while waiting for a concurrent test command.");
      }
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
