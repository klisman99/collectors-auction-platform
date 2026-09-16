package io.github.klisman99.collectorsauctionplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BiddingLateBidExtensionHttpIntegrationTests.ClockConfiguration.class)
class BiddingLateBidExtensionHttpIntegrationTests {

  private static final Instant INITIAL_TIME = Instant.parse("2026-09-14T12:00:00Z");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MutableClock clock;

  @BeforeEach
  void clearFixtures() {
    jdbcTemplate.update("DELETE FROM bid_attempts");
    jdbcTemplate.update("DELETE FROM accepted_bids");
    jdbcTemplate.update("DELETE FROM bidder_pseudonyms");
    jdbcTemplate.update("DELETE FROM auction_timeline_events");
    jdbcTemplate.update("DELETE FROM auction_item_snapshot_media");
    jdbcTemplate.update("DELETE FROM auctions");
    jdbcTemplate.update("DELETE FROM collectible_items");
    jdbcTemplate.update("DELETE FROM email_verification_tokens");
    jdbcTemplate.update("DELETE FROM password_recovery_tokens");
    jdbcTemplate.update("DELETE FROM regular_accounts");
    clock.set(INITIAL_TIME);
  }

  @Test
  void extendsTheCommittedDeadlineForEachEligibleBidInTheProtectionWindow() throws Exception {
    UUID sellerId = activeAccount("seller");
    UUID firstBidderId = activeAccount("first_bidder");
    UUID secondBidderId = activeAccount("second_bidder");
    UUID thirdBidderId = activeAccount("third_bidder");
    Instant originalEnd = INITIAL_TIME.plusSeconds(60);
    UUID auctionId = liveAuction(sellerId, originalEnd);
    UUID firstKey = UUID.randomUUID();
    UUID secondKey = UUID.randomUUID();

    mockMvc
        .perform(bidRequest(firstBidderId, auctionId, firstKey, 10_000))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.acceptedAt").value(INITIAL_TIME.toString()));

    assertCommittedDeadline(auctionId, INITIAL_TIME.plusSeconds(120));

    clock.set(originalEnd);
    mockMvc
        .perform(bidRequest(secondBidderId, auctionId, secondKey, 11_000))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.acceptedAt").value(originalEnd.toString()));

    Instant extendedEnd = INITIAL_TIME.plusSeconds(180);
    assertCommittedDeadline(auctionId, extendedEnd);

    clock.set(extendedEnd);
    mockMvc
        .perform(bidRequest(thirdBidderId, auctionId, UUID.randomUUID(), 12_000))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BID_LATE_OR_UNAVAILABLE"));
    mockMvc
        .perform(bidRequest(secondBidderId, auctionId, secondKey, 11_000))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DEDUPLICATED"))
        .andExpect(jsonPath("$.acceptedAt").value(originalEnd.toString()));

    assertCommittedDeadline(auctionId, extendedEnd);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accepted_bids WHERE auction_id = ?",
                Integer.class,
                auctionId))
        .isEqualTo(2);
  }

  @Test
  void acceptsABidAtTheInclusiveStartOfTheProtectionWindow() throws Exception {
    UUID sellerId = activeAccount("seller");
    UUID bidderId = activeAccount("bidder");
    Instant originalEnd = INITIAL_TIME.plusSeconds(120);
    UUID auctionId = liveAuction(sellerId, originalEnd);

    mockMvc
        .perform(bidRequest(bidderId, auctionId, UUID.randomUUID(), 10_000))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.acceptedAt").value(INITIAL_TIME.toString()));

    assertCommittedDeadline(auctionId, originalEnd);

    UUID justInsideAuctionId = liveAuction(sellerId, originalEnd);
    clock.set(INITIAL_TIME.plusSeconds(1));
    mockMvc
        .perform(bidRequest(bidderId, justInsideAuctionId, UUID.randomUUID(), 10_000))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.acceptedAt").value(INITIAL_TIME.plusSeconds(1).toString()));

    assertCommittedDeadline(justInsideAuctionId, originalEnd.plusSeconds(1));
  }

  @Test
  void rejectsTheFirstBidAtTheOriginalEffectiveDeadline() throws Exception {
    UUID sellerId = activeAccount("seller");
    UUID bidderId = activeAccount("bidder");
    Instant originalEnd = INITIAL_TIME.plusSeconds(60);
    UUID auctionId = liveAuction(sellerId, originalEnd);
    clock.set(originalEnd);

    mockMvc
        .perform(bidRequest(bidderId, auctionId, UUID.randomUUID(), 10_000))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BID_LATE_OR_UNAVAILABLE"));

    assertCommittedDeadline(auctionId, originalEnd);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accepted_bids WHERE auction_id = ?",
                Integer.class,
                auctionId))
        .isZero();
  }

  private void assertCommittedDeadline(UUID auctionId, Instant expected) throws Exception {
    mockMvc
        .perform(get("/api/v1/auctions/{id}", auctionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectiveEndAt").value(expected.toString()));
    assertThat(
            jdbcTemplate
                .queryForObject(
                    "SELECT ends_at FROM auctions WHERE id = ?", Timestamp.class, auctionId)
                .toInstant())
        .isEqualTo(expected);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bidRequest(
      UUID bidderId, UUID auctionId, UUID idempotencyKey, long amountCents) {
    return post("/api/v1/auctions/{id}/bids", auctionId)
        .with(user(bidderId.toString()).authorities(new SimpleGrantedAuthority("TRADING_ELIGIBLE")))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"amountCents\":" + amountCents + ",\"idempotencyKey\":\"" + idempotencyKey + "\"}");
  }

  private UUID activeAccount(String prefix) {
    UUID accountId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
            (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, 'unused', 'ACTIVE', ?, ?)
        """,
        accountId,
        accountId + "@example.com",
        prefix + "_" + accountId.toString().substring(0, 8),
        Timestamp.from(INITIAL_TIME),
        Timestamp.from(INITIAL_TIME));
    return accountId;
  }

  private UUID liveAuction(UUID sellerId, Instant endsAt) {
    UUID itemId = UUID.randomUUID();
    UUID auctionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO collectible_items
            (id, owner_id, category, title, description, condition, condition_notes,
             ownership_declared, status, created_at, updated_at, auction_locked_at)
        VALUES (?, ?, 'CARDS', 'Late bid fixture', 'A clock-controlled bidding fixture.', 'EXCELLENT',
                'No visible wear.', TRUE, 'APPROVED', ?, ?, ?)
        """,
        itemId,
        sellerId,
        Timestamp.from(INITIAL_TIME),
        Timestamp.from(INITIAL_TIME),
        Timestamp.from(INITIAL_TIME));
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
        VALUES (?, ?, ?, ?, 'fixture_seller', 'LIVE', 10000, 10000, 1000, ?, ?, ?, 'CARDS',
                'Late bid fixture', 'A clock-controlled bidding fixture.', 'EXCELLENT',
                'No visible wear.', TRUE, 'ENGLISH_ASCENDING', 'BRL', 1000, 100000000, 300, 600,
                604800, 120)
        """,
        auctionId,
        itemId,
        itemId,
        sellerId,
        Timestamp.from(INITIAL_TIME.minusSeconds(60)),
        Timestamp.from(endsAt),
        Timestamp.from(INITIAL_TIME.minusSeconds(120)));
    return auctionId;
  }

  @TestConfiguration
  static class ClockConfiguration {

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(INITIAL_TIME);
    }
  }

  static final class MutableClock extends Clock {
    private final AtomicReference<Instant> instant;

    MutableClock(Instant instant) {
      this.instant = new AtomicReference<>(instant);
    }

    void set(Instant value) {
      instant.set(value);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant.get();
    }
  }
}
