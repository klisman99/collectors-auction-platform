package io.github.klisman99.collectorsauctionplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BiddingHttpIntegrationTests {

  @Autowired private MockMvc mockMvc;
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
  void acceptsBidThroughHttpAndExposesOnlyPseudonymousPublicHistory() throws Exception {
    UUID sellerId = activeAccount("seller");
    UUID bidderId = activeAccount("secret_handle");
    UUID auctionId = liveAuction(sellerId);
    UUID key = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/bids", auctionId)
                .with(
                    user(bidderId.toString())
                        .authorities(new SimpleGrantedAuthority("TRADING_ELIGIBLE")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountCents\":10000,\"idempotencyKey\":\"" + key + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.sequence").value(1))
        .andExpect(jsonPath("$.acceptedAt").isNotEmpty())
        .andExpect(
            jsonPath("$.bidderPseudonym").value(org.hamcrest.Matchers.startsWith("Bidder-")));

    mockMvc
        .perform(get("/api/v1/auctions/{id}/bids", auctionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].amountCents").value(10_000))
        .andExpect(jsonPath("$[0].sequence").value(1))
        .andExpect(jsonPath("$[0].acceptedAt").isNotEmpty())
        .andExpect(
            jsonPath("$[0].bidderPseudonym").value(org.hamcrest.Matchers.startsWith("Bidder-")))
        .andExpect(jsonPath("$[0].bidderId").doesNotExist())
        .andExpect(jsonPath("$[0].publicHandle").doesNotExist());
    awaitAcceptedBidAudit(auctionId);

    jdbcTemplate.update(
        "UPDATE auctions SET ends_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minusSeconds(1)),
        auctionId);
    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/bids", auctionId)
                .with(
                    user(bidderId.toString())
                        .authorities(new SimpleGrantedAuthority("TRADING_ELIGIBLE")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountCents\":10000,\"idempotencyKey\":\"" + key + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DEDUPLICATED"))
        .andExpect(jsonPath("$.sequence").value(1));
  }

  private void awaitAcceptedBidAudit(UUID auctionId) throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    Integer count = 0;
    while (System.nanoTime() < deadline) {
      count =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM audit_records WHERE action = 'BID_ACCEPTED' AND target_id = ?",
              Integer.class,
              auctionId);
      if (count != null && count > 0) {
        return;
      }
      Thread.sleep(25);
    }
    org.assertj.core.api.Assertions.assertThat(count).isGreaterThan(0);
  }

  @Test
  void rejectsUnauthenticatedAndOperationalBiddingAtTheSecurityBoundary() throws Exception {
    UUID sellerId = activeAccount("seller");
    UUID auctionId = liveAuction(sellerId);
    String payload = "{\"amountCents\":10000,\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}";

    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/bids", auctionId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/bids", auctionId)
                .with(user("operator").roles("ADMINISTRATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isForbidden());
  }

  @Test
  void preservesRejectedReplayAndReturnsHttp429WithoutChangingAuctionState() throws Exception {
    UUID sellerId = activeAccount("seller");
    UUID bidderId = activeAccount("bidder");
    UUID auctionId = liveAuction(sellerId);
    UUID rejectedKey = UUID.randomUUID();

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(bidRequest(bidderId, auctionId, rejectedKey, 9_999))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("BID_AMOUNT_TOO_LOW"))
          .andExpect(jsonPath("$.requiredAmountCents").value(10_000));
    }
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT status FROM bid_attempts WHERE idempotency_key = ? ORDER BY received_at",
                String.class,
                rejectedKey))
        .containsExactly("REJECTED", "DEDUPLICATED");

    for (int attempt = 0; attempt < 8; attempt++) {
      mockMvc
          .perform(bidRequest(bidderId, auctionId, UUID.randomUUID(), 9_999))
          .andExpect(status().isUnprocessableEntity());
    }
    mockMvc
        .perform(bidRequest(bidderId, auctionId, UUID.randomUUID(), 9_999))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("BID_RATE_LIMITED"))
        .andExpect(jsonPath("$.ruleId").value("BR-AUTH-015"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT current_amount_cents FROM auctions WHERE id = ?", Long.class, auctionId))
        .isEqualTo(10_000L);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accepted_bids", Integer.class))
        .isZero();
  }

  @Test
  void retainsRejectedAttemptWhenAuctionDoesNotExist() throws Exception {
    UUID bidderId = activeAccount("bidder");
    UUID missingAuctionId = UUID.randomUUID();

    mockMvc
        .perform(bidRequest(bidderId, missingAuctionId, UUID.randomUUID(), 10_000))
        .andExpect(status().isNotFound());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bid_attempts WHERE auction_id = ?",
                Integer.class,
                missingAuctionId))
        .isEqualTo(1);
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
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
            (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, 'unused', 'ACTIVE', ?, ?)
        """,
        id,
        id + "@example.com",
        prefix + "_" + id.toString().substring(0, 8),
        Timestamp.from(now),
        Timestamp.from(now));
    return id;
  }

  private UUID liveAuction(UUID sellerId) {
    UUID itemId = UUID.randomUUID();
    UUID auctionId = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    jdbcTemplate.update(
        """
        INSERT INTO collectible_items
            (id, owner_id, category, title, description, condition, condition_notes,
             ownership_declared, status, created_at, updated_at, auction_locked_at)
        VALUES (?, ?, 'CARDS', 'HTTP bid fixture', 'A public bidding fixture.', 'EXCELLENT',
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
        VALUES (?, ?, ?, ?, 'fixture_seller', 'LIVE', 10000, 10000, 1000, ?, ?, ?, 'CARDS',
                'HTTP bid fixture', 'A public bidding fixture.', 'EXCELLENT', 'No visible wear.',
                TRUE, 'ENGLISH_ASCENDING', 'BRL', 1000, 100000000, 300, 600, 604800, 120)
        """,
        auctionId,
        itemId,
        itemId,
        sellerId,
        Timestamp.from(now.minusSeconds(60)),
        Timestamp.from(now.plusSeconds(3600)),
        Timestamp.from(now.minusSeconds(120)));
    return auctionId;
  }
}
