package io.github.klisman99.collectorsauctionplatform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuctionHttpIntegrationTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void clearAuctionFixtures() {
    jdbcTemplate.update("DELETE FROM auction_item_snapshot_media");
    jdbcTemplate.update("DELETE FROM auctions");
    jdbcTemplate.update("DELETE FROM collectible_item_media");
    jdbcTemplate.update("DELETE FROM collectible_items");
    jdbcTemplate.update("DELETE FROM regular_accounts");
  }

  @Test
  void sellerReschedulesAndLowersReserveWithoutChangingImmutablePublishedTerms() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = approvedItem(ownerId);
    Instant initialStart =
        Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    Instant initialEnd = initialStart.plus(2, ChronoUnit.HOURS);
    MvcResult scheduled =
        schedule(ownerId, itemId, 10_000, 1_000, 15_000L, initialStart, initialEnd);
    String auctionId =
        objectMapper.readTree(scheduled.getResponse().getContentAsString()).get("id").asText();
    Instant changedStart = initialStart.plus(30, ChronoUnit.MINUTES);
    Instant changedEnd = changedStart.plus(3, ChronoUnit.HOURS);

    mockMvc
        .perform(
            put("/api/v1/auctions/{id}/terms", auctionId)
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new EditableTermsRequest(12_000L, changedStart, changedEnd))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reserveAmountCents").value(12_000))
        .andExpect(jsonPath("$.startsAt").value(changedStart.toString()))
        .andExpect(jsonPath("$.endsAt").value(changedEnd.toString()))
        .andExpect(jsonPath("$.openingAmountCents").value(10_000))
        .andExpect(jsonPath("$.minimumIncrementCents").value(1_000))
        .andExpect(jsonPath("$.item.title").value("A rare approved card"));

    mockMvc
        .perform(
            put("/api/v1/auctions/{id}/terms", auctionId)
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new EditableTermsRequest(13_000L, changedStart, changedEnd))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.ruleId").value("BR-AUC-009"));
  }

  @Test
  void rejectsInvalidTermsAndItemsOwnedByAnotherAccount() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = approvedItem(ownerId);
    UUID otherAccountId = activeAccount();
    Instant startsAt = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);

    mockMvc
        .perform(
            post("/api/v1/auctions")
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ScheduleRequest(
                            itemId,
                            10_000,
                            1_000,
                            9_999L,
                            startsAt,
                            startsAt.plus(2, ChronoUnit.HOURS)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.ruleId").value("BR-AUC-003"));

    mockMvc
        .perform(
            post("/api/v1/auctions")
                .with(user(otherAccountId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ScheduleRequest(
                            itemId,
                            10_000,
                            1_000,
                            null,
                            startsAt,
                            startsAt.plus(2, ChronoUnit.HOURS)))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.ruleId").value("BR-ITEM-014"));
  }

  @Test
  void rejectsSchedulingForUnverifiedAndSuspendedOwners() throws Exception {
    UUID unverifiedOwner = account("ACTIVE", false);
    UUID unverifiedItem = approvedItemForExistingAccount(unverifiedOwner);
    UUID suspendedOwner = account("SUSPENDED", true);
    UUID suspendedItem = approvedItemForExistingAccount(suspendedOwner);
    Instant startsAt = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);

    for (var ownerAndItem :
        java.util.Map.of(unverifiedOwner, unverifiedItem, suspendedOwner, suspendedItem)
            .entrySet()) {
      mockMvc
          .perform(
              post("/api/v1/auctions")
                  .with(user(ownerAndItem.getKey().toString()).authorities(tradingEligible()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new ScheduleRequest(
                              ownerAndItem.getValue(),
                              10_000,
                              1_000,
                              null,
                              startsAt,
                              startsAt.plus(2, ChronoUnit.HOURS)))))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.ruleId").value("BR-AUTH-004"));
    }
  }

  @Test
  void schedulingLocksCatalogEditingAndPublishedItemSnapshotStaysStable() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = approvedItem(ownerId);
    Instant startsAt = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    MvcResult scheduled =
        schedule(
            ownerId, itemId, 10_000, 1_000, null, startsAt, startsAt.plus(2, ChronoUnit.HOURS));
    String auctionId =
        objectMapper.readTree(scheduled.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            put("/api/v1/catalog/drafts/{id}", itemId)
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "category":"CARDS",
                      "title":"A changed approved card",
                      "description":"This changed description must never affect the published auction.",
                      "condition":"EXCELLENT",
                      "conditionNotes":"Still in excellent collectible condition.",
                      "ownershipDeclared":true
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.ruleId").value("BR-ITEM-013"));

    jdbcTemplate.update(
        "UPDATE collectible_items SET title = 'Administratively changed source' WHERE id = ?",
        itemId);
    mockMvc
        .perform(get("/api/v1/auctions/{id}", auctionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.item.title").value("A rare approved card"));
  }

  @Test
  void ownerSchedulesApprovedItemAndOnlySellerSeesExactReserve() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = approvedItem(ownerId);
    Instant startsAt = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);

    MvcResult scheduled =
        mockMvc
            .perform(
                post("/api/v1/auctions")
                    .with(user(ownerId.toString()).authorities(tradingEligible()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new ScheduleRequest(itemId, 10_000, 1_000, 15_000L, startsAt, endsAt))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.item.title").value("A rare approved card"))
            .andExpect(jsonPath("$.openingAmountCents").value(10_000))
            .andExpect(jsonPath("$.minimumIncrementCents").value(1_000))
            .andExpect(jsonPath("$.reserveAmountCents").value(15_000))
            .andExpect(jsonPath("$.reserveMet").value(false))
            .andExpect(jsonPath("$.startsAt").value(startsAt.toString()))
            .andExpect(jsonPath("$.endsAt").value(endsAt.toString()))
            .andReturn();

    String auctionId =
        objectMapper.readTree(scheduled.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(get("/api/v1/auctions/{id}", auctionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reserveAmountCents").doesNotExist())
        .andExpect(jsonPath("$.reserveMet").value(false));

    mockMvc
        .perform(get("/api/v1/auctions/{id}", auctionId).with(user(ownerId.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reserveAmountCents").value(15_000));

    mockMvc
        .perform(
            get("/api/v1/auctions/{id}", auctionId)
                .with(user("administrator").roles("ADMINISTRATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reserveAmountCents").value(15_000));

    mockMvc
        .perform(get("/api/v1/auctions/{id}", auctionId).with(user("moderator").roles("MODERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reserveAmountCents").doesNotExist())
        .andExpect(jsonPath("$.reserveMet").value(false));

    mockMvc
        .perform(get("/api/v1/auctions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(auctionId))
        .andExpect(jsonPath("$[0].reserveAmountCents").doesNotExist());
  }

  private UUID approvedItem(UUID ownerId) {
    Instant now = Instant.now();
    activeAccount(ownerId, now);
    return approvedItemForExistingAccount(ownerId);
  }

  private UUID approvedItemForExistingAccount(UUID ownerId) {
    Instant now = Instant.now();
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
        "A rare approved card",
        "The immutable published description for this collectible card.",
        "Excellent condition with only minor signs of careful storage.",
        Timestamp.from(now),
        Timestamp.from(now));
    return itemId;
  }

  private UUID account(String status, boolean verified) {
    UUID accountId = UUID.randomUUID();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
            (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        accountId,
        accountId + "@example.com",
        "seller_" + accountId.toString().substring(0, 8),
        "unused-password-hash",
        status,
        Timestamp.from(now),
        verified ? Timestamp.from(now) : null);
    return accountId;
  }

  private UUID activeAccount() {
    UUID accountId = UUID.randomUUID();
    activeAccount(accountId, Instant.now());
    return accountId;
  }

  private void activeAccount(UUID accountId, Instant now) {
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
            (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        accountId,
        accountId + "@example.com",
        "seller_" + accountId.toString().substring(0, 8),
        "unused-password-hash",
        Timestamp.from(now),
        Timestamp.from(now));
  }

  private MvcResult schedule(
      UUID ownerId,
      UUID itemId,
      long openingAmountCents,
      long minimumIncrementCents,
      Long reserveAmountCents,
      Instant startsAt,
      Instant endsAt)
      throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auctions")
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ScheduleRequest(
                            itemId,
                            openingAmountCents,
                            minimumIncrementCents,
                            reserveAmountCents,
                            startsAt,
                            endsAt))))
        .andExpect(status().isCreated())
        .andReturn();
  }

  private SimpleGrantedAuthority tradingEligible() {
    return new SimpleGrantedAuthority("TRADING_ELIGIBLE");
  }

  private record ScheduleRequest(
      UUID itemId,
      long openingAmountCents,
      long minimumIncrementCents,
      Long reserveAmountCents,
      Instant startsAt,
      Instant endsAt) {}

  private record EditableTermsRequest(Long reserveAmountCents, Instant startsAt, Instant endsAt) {}
}
