package io.github.klisman99.collectorsauctionplatform.auctions;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
      "platform.identity.initial-administrator.email=auction-lock@example.com",
      "platform.identity.initial-administrator.password=auction lock administrator password"
    })
class AuctionSchedulingConcurrencyIntegrationTests {

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

  @Autowired private AuctionService auctions;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearAuctionFixtures() {
    jdbcTemplate.update("DELETE FROM bid_attempts");
    jdbcTemplate.update("DELETE FROM bid_disqualifications");
    jdbcTemplate.update("DELETE FROM accepted_bids");
    jdbcTemplate.update("DELETE FROM bidder_pseudonyms");
    jdbcTemplate.update("DELETE FROM auction_item_snapshot_media");
    jdbcTemplate.update("DELETE FROM auctions");
    jdbcTemplate.update("DELETE FROM collectible_item_media");
    jdbcTemplate.update("DELETE FROM collectible_items");
    jdbcTemplate.update("DELETE FROM regular_accounts");
  }

  @Test
  void persistsExactlyOneAuctionWhenTheSameItemIsScheduledConcurrently() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = approvedItem(ownerId);
    Instant startsAt = Instant.now().plus(15, ChronoUnit.MINUTES);
    ScheduleAuction command =
        new ScheduleAuction(
            itemId, 10_000, 1_000, 15_000L, startsAt, startsAt.plus(2, ChronoUnit.HOURS));
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> first = executor.submit(() -> schedule(start, ownerId, command));
      Future<Boolean> second = executor.submit(() -> schedule(start, ownerId, command));
      start.countDown();

      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM auctions", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT snapshot_title FROM auctions", String.class))
        .isEqualTo("Concurrent approved card");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT auction_locked_at IS NOT NULL FROM collectible_items WHERE id = ?",
                Boolean.class,
                itemId))
        .isTrue();
  }

  private boolean schedule(CountDownLatch start, UUID ownerId, ScheduleAuction command) {
    try {
      start.await(10, TimeUnit.SECONDS);
      auctions.schedule(ownerId, command);
      return true;
    } catch (AuctionApiException exception) {
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private UUID approvedItem(UUID ownerId) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
            (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        ownerId,
        ownerId + "@example.com",
        "seller_" + ownerId.toString().substring(0, 8),
        "unused-password-hash",
        Timestamp.from(now),
        Timestamp.from(now));
    UUID itemId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO collectible_items
            (id, owner_id, category, title, description, condition, condition_notes,
             ownership_declared, status, created_at, updated_at)
        VALUES (?, ?, 'CARDS', ?, ?, 'EXCELLENT', ?, TRUE, 'APPROVED', ?, ?)
        """,
        itemId,
        ownerId,
        "Concurrent approved card",
        "The immutable description used by both concurrent scheduling attempts.",
        "Excellent condition with complete notes for the published snapshot.",
        Timestamp.from(now),
        Timestamp.from(now));
    return itemId;
  }
}
