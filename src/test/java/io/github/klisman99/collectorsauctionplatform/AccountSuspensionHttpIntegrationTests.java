package io.github.klisman99.collectorsauctionplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSuspensionHttpIntegrationTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void clearFixtures() {
    jdbcTemplate.update("DELETE FROM spring_session_attributes");
    jdbcTemplate.update("DELETE FROM spring_session");
    jdbcTemplate.update("DELETE FROM bid_attempts");
    jdbcTemplate.update("DELETE FROM bid_disqualifications");
    jdbcTemplate.update("DELETE FROM accepted_bids");
    jdbcTemplate.update("DELETE FROM bidder_pseudonyms");
    jdbcTemplate.update("DELETE FROM auction_timeline_events");
    jdbcTemplate.update("DELETE FROM auction_item_snapshot_media");
    jdbcTemplate.update("DELETE FROM auctions");
    jdbcTemplate.update("DELETE FROM collectible_items");
    jdbcTemplate.update("DELETE FROM email_verification_tokens");
    jdbcTemplate.update("DELETE FROM password_recovery_tokens");
    jdbcTemplate.update("DELETE FROM regular_accounts");
  }

  @Test
  void revokesExistingSessionsAndAllowsOnlyRestrictedSignInAfterSuspension() throws Exception {
    String targetPassword = "suspended account password";
    UUID targetAccountId = activeAccount("session_target", targetPassword);
    MvcResult targetSession =
        signIn(targetAccountId + "@example.com", targetPassword)
            .andExpect(status().isOk())
            .andReturn();

    mockMvc
        .perform(
            post("/api/v1/admin/regular-accounts/{accountId}/suspension", targetAccountId)
                .with(user(UUID.randomUUID().toString()).roles("ADMINISTRATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reasonCategory":"FRAUD","publicReason":"The account is under review."}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/auth/session").cookie(sessionCookie(targetSession)))
        .andExpect(status().isUnauthorized());

    MvcResult restrictedSession =
        signIn(targetAccountId + "@example.com", targetPassword)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.canTrade").value(false))
            .andReturn();

    mockMvc
        .perform(get("/api/v1/auctions/mine").cookie(sessionCookie(restrictedSession)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.ruleId").value("BR-AUTH-009"));
  }

  @Test
  void suspendsSellerAuctionsAndPermanentlyDisqualifiesTheirBids() throws Exception {
    UUID administratorId = UUID.randomUUID();
    UUID sellerId = activeAccount("seller");
    UUID suspendedAccountId = activeAccount("suspended_37");
    UUID eligibleBidderId = activeAccount("eligible_37");
    UUID auctionWithBidsId = liveAuction(sellerId, "Bid recalculation fixture");
    UUID liveSellerAuctionId = liveAuction(suspendedAccountId, "Live seller suspension fixture");
    UUID scheduledSellerAuctionId =
        scheduledAuction(suspendedAccountId, "Scheduled seller suspension fixture");
    UUID terminalAuctionId = liveAuction(sellerId, "Terminal sale fixture");
    Instant originalEndBeforeExtension =
        Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS);
    jdbcTemplate.update(
        "UPDATE auctions SET ends_at = ? WHERE id = ?",
        Timestamp.from(originalEndBeforeExtension),
        auctionWithBidsId);
    Instant sellerAuctionEffectiveEndBeforeSuspension =
        jdbcTemplate.queryForObject(
            "SELECT ends_at FROM auctions WHERE id = ?", Instant.class, liveSellerAuctionId);

    placeBid(eligibleBidderId, auctionWithBidsId, 10_000);
    placeBid(suspendedAccountId, auctionWithBidsId, 11_000);
    Instant protectedEndBeforeSuspension =
        jdbcTemplate.queryForObject(
            "SELECT ends_at FROM auctions WHERE id = ?", Instant.class, auctionWithBidsId);
    assertThat(protectedEndBeforeSuspension).isAfter(originalEndBeforeExtension);
    placeBid(suspendedAccountId, terminalAuctionId, 10_000);
    jdbcTemplate.update(
        "UPDATE auctions SET state = 'SOLD', active_item_id = NULL, ended_at = ends_at WHERE id = ?",
        terminalAuctionId);
    UUID disqualifiedBidId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM accepted_bids WHERE auction_id = ? AND bidder_id = ?",
            UUID.class,
            auctionWithBidsId,
            suspendedAccountId);

    mockMvc
        .perform(
            post("/api/v1/admin/regular-accounts/{accountId}/suspension", suspendedAccountId)
                .with(user(administratorId.toString()).roles("ADMINISTRATOR"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reasonCategory": "SECURITY",
                      "publicReason": "The account is under a security review.",
                      "internalNote": "Preserve this note for the operations team."
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(suspendedAccountId.toString()))
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    awaitAuditRecord("REGULAR_ACCOUNT_SUSPENDED", suspendedAccountId);
    awaitAuditRecord("BID_DISQUALIFIED", disqualifiedBidId);

    mockMvc
        .perform(get("/api/v1/auctions/{id}", auctionWithBidsId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("LIVE"))
        .andExpect(jsonPath("$.currentAmountCents").value(10_000))
        .andExpect(jsonPath("$.nextMinimumAmountCents").value(11_000))
        .andExpect(jsonPath("$.reserveMet").value(false))
        .andExpect(jsonPath("$.effectiveEndAt").value(protectedEndBeforeSuspension.toString()));

    mockMvc
        .perform(get("/api/v1/auctions/{id}", liveSellerAuctionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SUSPENDED"))
        .andExpect(jsonPath("$.timeline[-1:].type").value("SUSPENDED"))
        .andExpect(
            jsonPath("$.timeline[-1:].publicReason")
                .value("The account is under a security review."))
        .andExpect(
            jsonPath("$.effectiveEndAt")
                .value(sellerAuctionEffectiveEndBeforeSuspension.toString()));

    mockMvc
        .perform(get("/api/v1/auctions/{id}", scheduledSellerAuctionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SUSPENDED"))
        .andExpect(jsonPath("$.timeline[-1:].type").value("SUSPENDED"));

    mockMvc
        .perform(get("/api/v1/auctions?state=SCHEDULED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(scheduledSellerAuctionId.toString()))
        .andExpect(jsonPath("$.content[0].state").value("SUSPENDED"));

    mockMvc
        .perform(get("/api/v1/auctions/{id}", terminalAuctionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SOLD"))
        .andExpect(jsonPath("$.currentAmountCents").value(10_000))
        .andExpect(jsonPath("$.nextMinimumAmountCents").value(11_000));
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM bid_disqualifications disqualification
                JOIN accepted_bids bid ON bid.id = disqualification.accepted_bid_id
                WHERE bid.auction_id = ?
                """,
                Integer.class,
                terminalAuctionId))
        .isZero();

    mockMvc
        .perform(get("/api/v1/auctions/{id}/bids", auctionWithBidsId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sequence").value(2))
        .andExpect(jsonPath("$[0].disqualified").value(true))
        .andExpect(jsonPath("$[0].bidderId").doesNotExist())
        .andExpect(jsonPath("$[0].publicHandle").doesNotExist())
        .andExpect(jsonPath("$[1].sequence").value(1))
        .andExpect(jsonPath("$[1].disqualified").value(false));

    mockMvc
        .perform(
            get("/api/v1/operations/auctions/{id}/bids", auctionWithBidsId)
                .with(user(administratorId.toString()).roles("ADMINISTRATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].bidderId").value(suspendedAccountId.toString()))
        .andExpect(jsonPath("$[0].bidderHandle").isNotEmpty())
        .andExpect(jsonPath("$[0].publicReason").value("The account is under a security review."))
        .andExpect(
            jsonPath("$[0].internalNote").value("Preserve this note for the operations team."));

    mockMvc
        .perform(
            get("/api/v1/operations/auctions/{id}/bids", auctionWithBidsId)
                .with(user(UUID.randomUUID().toString()).roles("MODERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].bidderId").value(suspendedAccountId.toString()))
        .andExpect(jsonPath("$[0].internalNote").doesNotExist());

    mockMvc
        .perform(
            post("/api/v1/admin/regular-accounts/{accountId}/reactivation", suspendedAccountId)
                .with(user(administratorId.toString()).roles("ADMINISTRATOR"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reasonCategory":"SECURITY","publicReason":"The review is complete."}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    awaitAuditRecord("REGULAR_ACCOUNT_REACTIVATED", suspendedAccountId);

    mockMvc
        .perform(get("/api/v1/auctions/{id}/bids", auctionWithBidsId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sequence").value(2))
        .andExpect(jsonPath("$[0].disqualified").value(true));
  }

  private void placeBid(UUID bidderId, UUID auctionId, long amountCents) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/bids", auctionId)
                .with(
                    user(bidderId.toString())
                        .authorities(new SimpleGrantedAuthority("TRADING_ELIGIBLE")))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"amountCents\":"
                        + amountCents
                        + ",\"idempotencyKey\":\""
                        + UUID.randomUUID()
                        + "\"}"))
        .andExpect(status().isCreated());
  }

  private UUID activeAccount(String prefix) {
    return activeAccount(prefix, "unused");
  }

  private UUID activeAccount(String prefix, String password) {
    UUID accountId = UUID.randomUUID();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
        (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        accountId,
        accountId + "@example.com",
        prefix + "_" + accountId.toString().substring(0, 8),
        passwordEncoder.encode(password),
        Timestamp.from(now),
        Timestamp.from(now));
    return accountId;
  }

  private UUID liveAuction(UUID sellerId, String title) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    return auction(
        sellerId,
        title,
        "LIVE",
        now.minusSeconds(60),
        now.plusSeconds(3600),
        now.minusSeconds(120));
  }

  private UUID scheduledAuction(UUID sellerId, String title) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    return auction(sellerId, title, "SCHEDULED", now.plusSeconds(3600), now.plusSeconds(7200), now);
  }

  private UUID auction(
      UUID sellerId,
      String title,
      String state,
      Instant startsAt,
      Instant endsAt,
      Instant scheduledAt) {
    UUID itemId = UUID.randomUUID();
    UUID auctionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO collectible_items
            (id, owner_id, category, title, description, condition, condition_notes,
             ownership_declared, status, created_at, updated_at, auction_locked_at)
        VALUES (?, ?, 'CARDS', ?, 'A public account-suspension fixture.', 'EXCELLENT',
                'No visible wear.', TRUE, 'APPROVED', ?, ?, ?)
        """,
        itemId,
        sellerId,
        title,
        Timestamp.from(scheduledAt),
        Timestamp.from(scheduledAt),
        Timestamp.from(scheduledAt));
    jdbcTemplate.update(
        """
        INSERT INTO auctions
             (id, item_id, active_item_id, seller_id, seller_handle, state,
             opening_amount_cents, current_amount_cents, minimum_increment_cents, reserve_amount_cents,
             starts_at, ends_at, scheduled_at, snapshot_category, snapshot_title,
             snapshot_description, snapshot_condition, snapshot_condition_notes,
             snapshot_ownership_declared, policy_auction_type, policy_currency,
             policy_minimum_amount_cents, policy_maximum_amount_cents,
             policy_minimum_lead_seconds, policy_minimum_duration_seconds,
             policy_maximum_duration_seconds, policy_protection_window_seconds)
        VALUES (?, ?, ?, ?, 'fixture_seller', ?, 10000, 10000, 1000, 12000, ?, ?, ?, 'CARDS',
                ?, 'A public account-suspension fixture.', 'EXCELLENT', 'No visible wear.',
                TRUE, 'ENGLISH_ASCENDING', 'BRL', 1000, 100000000, 300, 600, 604800, 120)
        """,
        auctionId,
        itemId,
        itemId,
        sellerId,
        state,
        Timestamp.from(startsAt),
        Timestamp.from(endsAt),
        Timestamp.from(scheduledAt),
        title);
    return auctionId;
  }

  private void awaitAuditRecord(String action, UUID targetId) throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      Integer count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM audit_records WHERE action = ? AND target_id = ?",
              Integer.class,
              action,
              targetId);
      if (count != null && count > 0) {
        return;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("Expected audit record " + action + " for " + targetId + ".");
  }

  private org.springframework.test.web.servlet.ResultActions signIn(String email, String password)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/sign-in")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
  }

  private Cookie sessionCookie(MvcResult result) {
    return java.util.Arrays.stream(result.getResponse().getCookies())
        .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Sign-in did not issue a session cookie."));
  }
}
