package io.github.klisman99.collectorsauctionplatform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuctionHttpIntegrationTests.TestMailConfiguration.class)
class AuctionHttpIntegrationTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private RecordingMailSender mailSender;

  @BeforeEach
  void clearAuctionFixtures() {
    mailSender.clear();
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
    awaitAudit("AUCTION_RESCHEDULED", UUID.fromString(auctionId));
    String rescheduleMetadata =
        jdbcTemplate.queryForObject(
            "SELECT metadata FROM audit_records WHERE action = 'AUCTION_RESCHEDULED' AND target_id = ?",
            String.class,
            UUID.fromString(auctionId));
    org.assertj.core.api.Assertions.assertThat(rescheduleMetadata)
        .contains("previousReserveAmountCents=15000", "currentReserveAmountCents=12000")
        .contains(changedStart.toString(), changedEnd.toString());

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
  void sellerCancelsScheduledAuctionWithPublicReasonAndReleasesUnchangedItem() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = approvedItem(ownerId);
    Instant startsAt = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    MvcResult scheduled =
        schedule(
            ownerId, itemId, 10_000, 1_000, 15_000L, startsAt, startsAt.plus(2, ChronoUnit.HOURS));
    String auctionId =
        objectMapper.readTree(scheduled.getResponse().getContentAsString()).get("id").asText();

    UUID otherAccountId = activeAccount();
    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/cancellation", auctionId)
                .with(user(otherAccountId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicReason\":\"Not my auction.\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/cancellation", auctionId)
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicReason\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.ruleId").value("BR-AUC-010"));

    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/cancellation", auctionId)
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicReason\":\"The collectible is no longer available.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CANCELLED"))
        .andExpect(jsonPath("$.reserveAmountCents").value(15_000))
        .andExpect(jsonPath("$.timeline[1].type").value("CANCELLED"))
        .andExpect(
            jsonPath("$.timeline[1].publicReason")
                .value("The collectible is no longer available."));

    Integer activeAuctions =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auctions WHERE active_item_id = ?", Integer.class, itemId);
    Instant itemLock =
        jdbcTemplate.queryForObject(
            "SELECT auction_locked_at FROM collectible_items WHERE id = ?", Instant.class, itemId);
    org.assertj.core.api.Assertions.assertThat(activeAuctions).isZero();
    org.assertj.core.api.Assertions.assertThat(itemLock).isNull();
    MimeMessage cancellationEmail = mailSender.awaitMessage();
    org.assertj.core.api.Assertions.assertThat(cancellationEmail.getSubject())
        .isEqualTo("Your auction was cancelled");
    org.assertj.core.api.Assertions.assertThat(cancellationEmail.getContent().toString())
        .contains("The collectible is no longer available.");
    awaitAudit("AUCTION_CANCELLED", UUID.fromString(auctionId));

    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/cancellation", auctionId)
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicReason\":\"Cancel again.\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.ruleId").value("BR-AUC-010"));
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
        .perform(get("/api/v1/auctions/mine").with(user(ownerId.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(auctionId))
        .andExpect(jsonPath("$[0].reserveAmountCents").value(15_000));

    mockMvc.perform(get("/api/v1/auctions/mine")).andExpect(status().isUnauthorized());

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
        .andExpect(jsonPath("$.content[0].id").value(auctionId))
        .andExpect(jsonPath("$.content[0].reserveAmountCents").doesNotExist());
  }

  @Test
  void anonymousDiscoveryPaginatesEachLifecycleViewAndDetailsRemainPrivacySafe() throws Exception {
    UUID ownerId = UUID.randomUUID();
    activeAccount(ownerId, Instant.now());
    Instant firstStart = Instant.now().plus(20, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    MvcResult laterScheduled =
        schedule(
            ownerId,
            approvedItemForExistingAccount(ownerId),
            10_000,
            1_000,
            15_000L,
            firstStart.plusSeconds(600),
            firstStart.plusSeconds(4200));
    MvcResult earlierScheduled =
        schedule(
            ownerId,
            approvedItemForExistingAccount(ownerId),
            10_000,
            1_000,
            null,
            firstStart,
            firstStart.plusSeconds(3600));
    String earlierId =
        objectMapper
            .readTree(earlierScheduled.getResponse().getContentAsString())
            .get("id")
            .asText();

    mockMvc
        .perform(
            get("/api/v1/auctions")
                .param("state", "SCHEDULED")
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(earlierId))
        .andExpect(jsonPath("$.content[0].reserveAmountCents").doesNotExist())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2));

    mockMvc
        .perform(get("/api/v1/auctions/{id}", earlierId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentAmountCents").value(10_000))
        .andExpect(jsonPath("$.effectiveEndAt").value(firstStart.plusSeconds(3600).toString()))
        .andExpect(jsonPath("$.eligibleBidHistory").isArray())
        .andExpect(jsonPath("$.disqualifications").isArray())
        .andExpect(jsonPath("$.timeline[0].type").value("SCHEDULED"));

    String laterId =
        objectMapper.readTree(laterScheduled.getResponse().getContentAsString()).get("id").asText();
    org.assertj.core.api.Assertions.assertThat(laterId).isNotEqualTo(earlierId);

    jdbcTemplate.update(
        "UPDATE auctions SET state = 'LIVE' WHERE id IN (?, ?)", earlierId, laterId);
    mockMvc
        .perform(get("/api/v1/auctions").param("state", "LIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(earlierId))
        .andExpect(jsonPath("$.content[1].id").value(laterId));
    jdbcTemplate.update(
        "UPDATE auctions SET state = 'SCHEDULED' WHERE id IN (?, ?)", earlierId, laterId);

    cancel(ownerId, earlierId, "The earlier auction ended first.");
    Thread.sleep(2);
    cancel(ownerId, laterId, "The later auction ended most recently.");
    mockMvc
        .perform(get("/api/v1/auctions").param("state", "ENDED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(laterId))
        .andExpect(jsonPath("$.content[1].id").value(earlierId));
  }

  @Test
  void operationsSuspendWithLayeredPrivacyAndOnlyAdministratorCanResolve() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = approvedItem(ownerId);
    Instant startsAt = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    MvcResult scheduled =
        schedule(
            ownerId, itemId, 10_000, 1_000, null, startsAt, startsAt.plus(2, ChronoUnit.HOURS));
    String auctionId =
        objectMapper.readTree(scheduled.getResponse().getContentAsString()).get("id").asText();
    UUID moderatorId = UUID.randomUUID();
    UUID administratorId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/operations/auctions/{id}/suspension", auctionId)
                .with(user(moderatorId.toString()).roles("MODERATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reasonCategory":"POLICY_REVIEW",
                     "publicReason":"The listing requires an operational review.",
                     "internalNote":"Check the private provenance document."}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SUSPENDED"))
        .andExpect(jsonPath("$.sourceState").value("SCHEDULED"))
        .andExpect(jsonPath("$.timeline[1].reasonCategory").value("POLICY_REVIEW"))
        .andExpect(jsonPath("$.timeline[1].internalNote").doesNotExist());

    for (var participant :
        java.util.List.of(
            user(ownerId.toString()).authorities(tradingEligible()),
            user(moderatorId.toString()).roles("MODERATOR"))) {
      mockMvc
          .perform(get("/api/v1/auctions/{id}", auctionId).with(participant))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.timeline[1].publicReason")
                  .value("The listing requires an operational review."))
          .andExpect(jsonPath("$.timeline[1].internalNote").doesNotExist());
    }

    mockMvc
        .perform(
            post("/api/v1/operations/auctions/{id}/release", auctionId)
                .with(user(moderatorId.toString()).roles("MODERATOR"))
                .with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUCTION_ADMINISTRATOR_REQUIRED"));

    mockMvc
        .perform(
            get("/api/v1/operations/auctions/suspended")
                .with(user(administratorId.toString()).roles("ADMINISTRATOR")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].timeline[1].internalNote")
                .value("Check the private provenance document."));

    mockMvc
        .perform(get("/api/v1/auctions/{id}", auctionId))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.timeline[1].publicReason")
                .value("The listing requires an operational review."))
        .andExpect(jsonPath("$.timeline[1].internalNote").doesNotExist());

    mockMvc
        .perform(
            post("/api/v1/operations/auctions/{id}/release", auctionId)
                .with(user(administratorId.toString()).roles("ADMINISTRATOR"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("DRAFT"));

    Instant rescheduledStart = startsAt.plus(1, ChronoUnit.HOURS);
    mockMvc
        .perform(get("/api/v1/auctions/mine").with(user(ownerId.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].state").value("DRAFT"));
    mockMvc
        .perform(
            put("/api/v1/auctions/{id}/terms", auctionId)
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new EditableTermsRequest(
                            null, rescheduledStart, rescheduledStart.plus(2, ChronoUnit.HOURS)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SCHEDULED"));
    org.assertj.core.api.Assertions.assertThat(
            jdbcTemplate.queryForObject(
                "SELECT auction_locked_at FROM collectible_items WHERE id = ?",
                Instant.class,
                itemId))
        .isNotNull();
  }

  @Test
  void administratorCancellationRequiresAndAppliesExplicitItemDisposition() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = approvedItem(ownerId);
    Instant startsAt = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    String auctionId =
        objectMapper
            .readTree(
                schedule(
                        ownerId,
                        itemId,
                        10_000,
                        1_000,
                        null,
                        startsAt,
                        startsAt.plus(2, ChronoUnit.HOURS))
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();
    UUID administratorId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/operations/auctions/{id}/suspension", auctionId)
                .with(user(administratorId.toString()).roles("ADMINISTRATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reasonCategory":"ITEM_CONCERN","publicReason":"The item needs review."}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/operations/auctions/{id}/cancellation", auctionId)
                .with(user(administratorId.toString()).roles("ADMINISTRATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reasonCategory":"ITEM_CONCERN",
                     "publicReason":"Approval was revoked after review.",
                     "internalNote":"Evidence retained by operations.",
                     "itemDisposition":"REVOKE_APPROVAL_TO_DRAFT"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CANCELLED"))
        .andExpect(jsonPath("$.timeline[2].itemDisposition").value("REVOKE_APPROVAL_TO_DRAFT"));

    org.assertj.core.api.Assertions.assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM collectible_items WHERE id = ?", String.class, itemId))
        .isEqualTo("DRAFT");
    org.assertj.core.api.Assertions.assertThat(
            jdbcTemplate.queryForObject(
                "SELECT auction_locked_at FROM collectible_items WHERE id = ?",
                Instant.class,
                itemId))
        .isNull();
    awaitAudit("AUCTION_ADMINISTRATIVELY_CANCELLED", UUID.fromString(auctionId));
    String cancellationMetadata =
        jdbcTemplate.queryForObject(
            "SELECT metadata FROM audit_records WHERE action = 'AUCTION_ADMINISTRATIVELY_CANCELLED' AND target_id = ?",
            String.class,
            UUID.fromString(auctionId));
    org.assertj.core.api.Assertions.assertThat(cancellationMetadata)
        .contains(
            "reasonCategory=ITEM_CONCERN",
            "publicReason=Approval was revoked after review.",
            "internalNote=Evidence retained by operations.",
            "itemDisposition=REVOKE_APPROVAL_TO_DRAFT");
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

  private void cancel(UUID ownerId, String auctionId, String reason) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auctions/{id}/cancellation", auctionId)
                .with(user(ownerId.toString()).authorities(tradingEligible()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("publicReason", reason))))
        .andExpect(status().isOk());
  }

  private SimpleGrantedAuthority tradingEligible() {
    return new SimpleGrantedAuthority("TRADING_ELIGIBLE");
  }

  private void awaitAudit(String action, UUID targetId) throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    Integer count = 0;
    while (System.nanoTime() < deadline) {
      count =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM audit_records WHERE action = ? AND target_id = ?",
              Integer.class,
              action,
              targetId);
      if (count != null && count > 0) {
        return;
      }
      Thread.sleep(25);
    }
    org.assertj.core.api.Assertions.assertThat(count).isGreaterThan(0);
  }

  private record ScheduleRequest(
      UUID itemId,
      long openingAmountCents,
      long minimumIncrementCents,
      Long reserveAmountCents,
      Instant startsAt,
      Instant endsAt) {}

  private record EditableTermsRequest(Long reserveAmountCents, Instant startsAt, Instant endsAt) {}

  @TestConfiguration
  static class TestMailConfiguration {

    @Bean
    @Primary
    RecordingMailSender recordingMailSender() {
      return new RecordingMailSender();
    }
  }

  static class RecordingMailSender extends JavaMailSenderImpl {
    private final CopyOnWriteArrayList<MimeMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void send(MimeMessage message) {
      messages.add(message);
    }

    void clear() {
      messages.clear();
    }

    MimeMessage awaitMessage() throws InterruptedException, MessagingException {
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
      while (messages.isEmpty() && System.nanoTime() < deadline) {
        Thread.sleep(25);
      }
      org.assertj.core.api.Assertions.assertThat(messages).isNotEmpty();
      return messages.getLast();
    }
  }
}
