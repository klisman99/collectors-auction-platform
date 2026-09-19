package io.github.klisman99.collectorsauctionplatform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditHistoryHttpIntegrationTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private AuditProjectionStore auditRecords;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearAuditHistory() {
    jdbcTemplate.update("DELETE FROM audit_record_participants");
    jdbcTemplate.update("DELETE FROM audit_records");
  }

  @Test
  void keepsParticipantAndPublicViewsFreeOfInternalAndOperationalDetails() throws Exception {
    UUID sellerId = UUID.randomUUID();
    UUID bidderId = UUID.randomUUID();
    UUID auctionId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-19T12:00:00Z");

    auditRecords.append(
        AuditRecord.operationalAuctionAction(
                AuditRecord.AuditAction.AUCTION_SUSPENDED,
                UUID.randomUUID(),
                auctionId,
                occurredAt,
                "currentReserveAmountCents=45000;internalNote=Only administrators can read this")
            .participates(sellerId)
            .forAuction(auctionId)
            .withPublicDetails(
                "POLICY",
                "The auction was paused while the listing was reviewed.",
                null,
                null,
                "Only administrators can read this"));
    auditRecords.append(
        AuditRecord.accountAuctionAction(
                AuditRecord.AuditAction.BID_ACCEPTED,
                bidderId,
                auctionId,
                occurredAt.plusSeconds(1),
                "amountCents=15000;bidderPseudonym=Bidder-A1B2C3D4")
            .participates(bidderId)
            .forAuction(auctionId)
            .withPublicDetails(null, null, 15_000L, "Bidder-A1B2C3D4", null));

    mockMvc
        .perform(
            get("/api/v1/history/mine").with(user(sellerId.toString()).roles("REGULAR_ACCOUNT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].action").value("AUCTION_SUSPENDED"))
        .andExpect(jsonPath("$.content[0].metadata").doesNotExist())
        .andExpect(content().string(not(containsString("currentReserveAmountCents"))))
        .andExpect(content().string(not(containsString("Only administrators can read this"))));

    mockMvc
        .perform(
            get("/api/v1/history/mine")
                .with(user(UUID.randomUUID().toString()).roles("REGULAR_ACCOUNT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    mockMvc
        .perform(get("/api/v1/auctions/{auctionId}/timeline", auctionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].action").value("BID_ACCEPTED"))
        .andExpect(jsonPath("$.content[0].bidderPseudonym").value("Bidder-A1B2C3D4"))
        .andExpect(jsonPath("$.content[0].amountCents").value(15_000))
        .andExpect(jsonPath("$.content[0].actorId").doesNotExist())
        .andExpect(content().string(not(containsString("currentReserveAmountCents"))))
        .andExpect(content().string(not(containsString("Only administrators can read this"))));
  }

  @Test
  void restrictsModeratorHistoryAndLetsAdministratorsInspectCompleteFacts() throws Exception {
    UUID auctionId = UUID.randomUUID();
    UUID administratorId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-19T12:00:00Z");

    auditRecords.append(
        AuditRecord.operationalAuctionAction(
                AuditRecord.AuditAction.AUCTION_SUSPENDED,
                administratorId,
                auctionId,
                occurredAt,
                "currentReserveAmountCents=45000;internalNote=Retain evidence E-42")
            .forAuction(auctionId)
            .withPublicDetails(
                "POLICY",
                "The auction was paused while the listing was reviewed.",
                null,
                null,
                "Retain evidence E-42"));
    auditRecords.append(
        AuditRecord.operationalRegularAccountAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_SUSPENDED,
                administratorId,
                UUID.randomUUID(),
                occurredAt.plusSeconds(1),
                "internalNote=Unrelated account investigation")
            .withPublicDetails("FRAUD", "The account is under review.", null, null, "private"));

    mockMvc
        .perform(
            get("/api/v1/operations/audit-records")
                .with(user(UUID.randomUUID().toString()).roles("MODERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].action").value("AUCTION_SUSPENDED"))
        .andExpect(jsonPath("$.content[0].internalNote").value("Retain evidence E-42"))
        .andExpect(content().string(not(containsString("currentReserveAmountCents"))));

    mockMvc
        .perform(
            get("/api/v1/admin/history")
                .with(user(administratorId.toString()).roles("ADMINISTRATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[1].actorId").value(administratorId.toString()))
        .andExpect(
            jsonPath("$.content[1].metadata")
                .value(containsString("currentReserveAmountCents=45000")))
        .andExpect(jsonPath("$.content[1].internalNote").value("Retain evidence E-42"));
  }

  @Test
  void ordersPagesByServerTimeAndDoesNotDuplicateAReplayedProjection() throws Exception {
    UUID accountId = UUID.randomUUID();
    Instant first = Instant.parse("2026-09-19T12:00:00Z");
    Instant second = first.plusSeconds(1);

    AuditRecord firstRecord =
        AuditRecord.accountAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_VERIFIED,
                accountId,
                first,
                "verification=completed")
            .participates(accountId);
    auditRecords.append(firstRecord);
    auditRecords.append(
        AuditRecord.accountAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_VERIFIED,
                accountId,
                first,
                "verification=completed")
            .participates(accountId));
    auditRecords.append(
        AuditRecord.accountAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_PASSWORD_RESET,
                accountId,
                second,
                "password=reset")
            .participates(accountId));

    mockMvc
        .perform(
            get("/api/v1/history/mine?page=0&size=1")
                .with(user(accountId.toString()).roles("REGULAR_ACCOUNT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].action").value("REGULAR_ACCOUNT_PASSWORD_RESET"));

    Integer records =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_records", Integer.class);
    assertThat(records).isEqualTo(2);
  }
}
