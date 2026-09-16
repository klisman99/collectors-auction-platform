package io.github.klisman99.collectorsauctionplatform.auctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.klisman99.collectorsauctionplatform.CollectorsAuctionPlatformApplication;
import io.github.klisman99.collectorsauctionplatform.accountadministration.AccountAdministrationService;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountSuspensionReasonCategory;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  AuctionLifecycleIntegrationTests.ClockConfiguration.class,
  AuctionLifecycleIntegrationTests.TestMailConfiguration.class
})
@Testcontainers(disabledWithoutDocker = true)
class AuctionLifecycleIntegrationTests {

  private static final Instant INITIAL_TIME = Instant.parse("2026-09-10T12:00:00Z");
  private static final AtomicReference<Instant> NEW_CONTEXT_TIME =
      new AtomicReference<>(INITIAL_TIME);

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:18.6-alpine")
          .withDatabaseName("auction_lifecycle")
          .withUsername("collectors")
          .withPassword("collectors");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired private AuctionService auctions;

  @Autowired private AuctionLifecycle lifecycle;

  @Autowired private AuctionBidding auctionBidding;

  @Autowired private AccountAdministrationService accountAdministration;

  @Autowired private MutableClock clock;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MockMvc mockMvc;

  @Autowired private RecordingMailSender mailSender;

  @BeforeEach
  void clearFixtures() {
    jdbcTemplate.update("DELETE FROM sales");
    jdbcTemplate.update("DELETE FROM bid_attempts");
    jdbcTemplate.update("DELETE FROM bid_disqualifications");
    jdbcTemplate.update("DELETE FROM accepted_bids");
    jdbcTemplate.update("DELETE FROM bidder_pseudonyms");
    jdbcTemplate.update("DELETE FROM auction_timeline_events");
    jdbcTemplate.update("DELETE FROM auction_item_snapshot_media");
    jdbcTemplate.update("DELETE FROM auctions");
    jdbcTemplate.update("DELETE FROM collectible_item_media");
    jdbcTemplate.update("DELETE FROM collectible_items");
    NEW_CONTEXT_TIME.set(INITIAL_TIME);
    clock.set(INITIAL_TIME);
    mailSender.clear();
  }

  @Test
  void startsAtTheExactAuthoritativeStartInstantAndDeliversLifecycleReactions() throws Exception {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));

    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();

    Auction started = auctions.get(auction.id());
    assertThat(started.state()).isEqualTo(Auction.State.LIVE);
    assertThat(started.timeline())
        .extracting(AuctionTimelineEntry::type)
        .containsExactly(AuctionTimelineEntry.Type.SCHEDULED, AuctionTimelineEntry.Type.STARTED);
    assertThat(mailSender.awaitMessage().getSubject()).isEqualTo("Your auction is live");
    awaitAudit("AUCTION_STARTED", auction.id());
  }

  @Test
  void restartAfterTheIntervalClosesAuctionOnceAndReleasesItem() throws InterruptedException {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));

    NEW_CONTEXT_TIME.set(auction.endsAt());
    try (ConfigurableApplicationContext ignored = restartApplication()) {
      // ApplicationReadyEvent performs reconciliation before run() returns.
    }
    lifecycle.reconcileDueAuctions();

    Auction ended = awaitAuctionState(auction.id(), Auction.State.UNSOLD);
    assertThat(ended.state()).isEqualTo(Auction.State.UNSOLD);
    assertThat(ended.endedAt()).isEqualTo(auction.endsAt());
    assertThat(ended.timeline())
        .extracting(AuctionTimelineEntry::type)
        .containsExactly(AuctionTimelineEntry.Type.SCHEDULED, AuctionTimelineEntry.Type.ENDED);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT auction_locked_at FROM collectible_items WHERE id = ?",
                Instant.class,
                auction.itemId()))
        .isNull();
  }

  @Test
  void closesAnEligibleDueAuctionAsSoldAndCreatesOneImmutableSale() throws Exception {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));
    UUID bidderId = activeAccount("closing_bidder");

    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();
    clock.set(auction.endsAt().minusSeconds(121));
    mockMvc.perform(bidRequest(bidderId, auction.id(), 10_000)).andExpect(status().isCreated());

    clock.set(auction.endsAt());
    lifecycle.reconcileDueAuctions();

    Auction closed = awaitAuctionState(auction.id(), Auction.State.SOLD);
    awaitSaleCount(auction.id(), 1);
    assertThat(closed.state()).isEqualTo(Auction.State.SOLD);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sales WHERE auction_id = ?", Integer.class, auction.id()))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT buyer_id FROM sales WHERE auction_id = ?", UUID.class, auction.id()))
        .isEqualTo(bidderId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT amount_cents FROM sales WHERE auction_id = ?", Long.class, auction.id()))
        .isEqualTo(10_000L);
    String bidderHandle =
        jdbcTemplate.queryForObject(
            "SELECT public_handle FROM regular_accounts WHERE id = ?", String.class, bidderId);
    mockMvc
        .perform(get("/api/v1/auctions/{id}", auction.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalOutcome.amountCents").value(10_000))
        .andExpect(jsonPath("$.finalOutcome.bidderPseudonym").isNotEmpty())
        .andExpect(jsonPath("$.finalOutcome.bidderHandle").doesNotExist());
    mockMvc
        .perform(
            get("/api/v1/auctions/{id}", auction.id())
                .with(user(auction.sellerId().toString()).authorities(tradingEligible())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalOutcome.bidderHandle").value(bidderHandle));
    assertThat(mailSender.awaitMessages(3))
        .extracting(message -> message.getSubject())
        .contains("Your auction sold", "You won an auction");
    awaitAudit("AUCTION_SOLD", auction.id());

    lifecycle.reconcileDueAuctions();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sales WHERE auction_id = ?", Integer.class, auction.id()))
        .isEqualTo(1);
  }

  @Test
  void concurrentClosingWorkersPersistOneSoldOutcomeAndOneSale() throws Exception {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));
    UUID bidderId = activeAccount("closing_workers");

    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();
    clock.set(auction.endsAt().minusSeconds(121));
    mockMvc.perform(bidRequest(bidderId, auction.id(), 10_000)).andExpect(status().isCreated());
    clock.set(auction.endsAt());

    ExecutorService workers = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> completions =
          List.of(
              workers.submit(() -> reconcileWhenReleased(ready, start)),
              workers.submit(() -> reconcileWhenReleased(ready, start)));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<?> completion : completions) {
        completion.get(5, TimeUnit.SECONDS);
      }
    } finally {
      workers.shutdownNow();
      workers.awaitTermination(5, TimeUnit.SECONDS);
    }

    assertThat(awaitAuctionState(auction.id(), Auction.State.SOLD).finalAmountCents())
        .isEqualTo(10_000L);
    awaitSaleCount(auction.id(), 1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sales WHERE auction_id = ?", Integer.class, auction.id()))
        .isEqualTo(1);
  }

  @Test
  void selectsTheHighestEligibleBidForTheSoldOutcome() throws Exception {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));
    UUID firstBidderId = activeAccount("closing_first");
    UUID highestBidderId = activeAccount("closing_highest");

    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();
    clock.set(auction.endsAt().minusSeconds(121));
    mockMvc
        .perform(bidRequest(firstBidderId, auction.id(), 10_000))
        .andExpect(status().isCreated());
    mockMvc
        .perform(bidRequest(highestBidderId, auction.id(), 11_000))
        .andExpect(status().isCreated());

    clock.set(auction.endsAt());
    lifecycle.reconcileDueAuctions();

    Auction closed = awaitAuctionState(auction.id(), Auction.State.SOLD);
    awaitSaleCount(auction.id(), 1);
    assertThat(closed.finalAmountCents()).isEqualTo(11_000L);
    assertThat(closed.finalBidderId()).isEqualTo(highestBidderId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT buyer_id FROM sales WHERE auction_id = ?", UUID.class, auction.id()))
        .isEqualTo(highestBidderId);
  }

  @Test
  void disqualifyingTheHighestBidFallsBackToTheNextEligibleBid() throws Exception {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));
    UUID firstBidderId = activeAccount("fallback_first");
    UUID highestBidderId = activeAccount("fallback_highest");

    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();
    clock.set(auction.endsAt().minusSeconds(121));
    mockMvc
        .perform(bidRequest(firstBidderId, auction.id(), 10_000))
        .andExpect(status().isCreated());
    mockMvc
        .perform(bidRequest(highestBidderId, auction.id(), 11_000))
        .andExpect(status().isCreated());
    accountAdministration.suspend(
        UUID.randomUUID(),
        highestBidderId,
        RegularAccountSuspensionReasonCategory.SECURITY,
        "The highest bidder account requires a security review.",
        null);

    clock.set(auction.endsAt());
    lifecycle.reconcileDueAuctions();

    Auction closed = awaitAuctionState(auction.id(), Auction.State.SOLD);
    awaitSaleCount(auction.id(), 1);
    assertThat(closed.finalAmountCents()).isEqualTo(10_000L);
    assertThat(closed.finalBidderId()).isEqualTo(firstBidderId);
  }

  @Test
  void sellsWhenTheHighestEligibleBidExactlyMeetsTheReserve() throws Exception {
    Auction auction =
        scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900), 11_000L);
    UUID bidderId = activeAccount("reserve_met_bidder");

    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();
    clock.set(auction.endsAt().minusSeconds(121));
    mockMvc.perform(bidRequest(bidderId, auction.id(), 11_000)).andExpect(status().isCreated());
    clock.set(auction.endsAt());
    lifecycle.reconcileDueAuctions();

    Auction closed = awaitAuctionState(auction.id(), Auction.State.SOLD);
    awaitSaleCount(auction.id(), 1);
    assertThat(closed.finalAmountCents()).isEqualTo(11_000L);
    assertThat(closed.finalBidderId()).isEqualTo(bidderId);
  }

  @Test
  void bidCommandRacingTheDueClosingClaimIsRejectedAndCannotCreateASale() throws Exception {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));
    UUID bidderId = activeAccount("closing_race_bidder");

    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();
    clock.set(auction.endsAt());

    ExecutorService workers = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<org.springframework.test.web.servlet.MvcResult> bid =
          workers.submit(() -> bidWhenReleased(ready, start, bidderId, auction.id()));
      Future<?> closing = workers.submit(() -> reconcileWhenReleased(ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(bid.get(5, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(409);
      closing.get(5, TimeUnit.SECONDS);
    } finally {
      workers.shutdownNow();
      workers.awaitTermination(5, TimeUnit.SECONDS);
    }

    assertThat(awaitAuctionState(auction.id(), Auction.State.UNSOLD).finalBidId()).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM accepted_bids WHERE auction_id = ?",
                Integer.class,
                auction.id()))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sales WHERE auction_id = ?", Integer.class, auction.id()))
        .isZero();
  }

  @Test
  void recordsTheHighestBelowReserveBidWithoutCreatingASale() throws Exception {
    Auction auction =
        scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900), 11_000L);
    UUID bidderId = activeAccount("below_reserve_bidder");

    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();
    clock.set(auction.endsAt().minusSeconds(121));
    mockMvc.perform(bidRequest(bidderId, auction.id(), 10_000)).andExpect(status().isCreated());
    clock.set(auction.endsAt());
    lifecycle.reconcileDueAuctions();

    Auction awaitingDecision =
        awaitAuctionState(auction.id(), Auction.State.AWAITING_SELLER_DECISION);
    assertThat(awaitingDecision.finalAmountCents()).isEqualTo(10_000L);
    assertThat(awaitingDecision.finalBidderId()).isEqualTo(bidderId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sales WHERE auction_id = ?", Integer.class, auction.id()))
        .isZero();
  }

  @Test
  void closesAsUnsoldWhenTheOnlyAcceptedBidWasDisqualifiedBeforeSelection() throws Exception {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));
    UUID bidderId = activeAccount("disqualified_bidder");

    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();
    clock.set(auction.endsAt().minusSeconds(121));
    mockMvc.perform(bidRequest(bidderId, auction.id(), 10_000)).andExpect(status().isCreated());
    accountAdministration.suspend(
        UUID.randomUUID(),
        bidderId,
        RegularAccountSuspensionReasonCategory.SECURITY,
        "The bidder account requires a security review.",
        null);

    clock.set(auction.endsAt());
    lifecycle.reconcileDueAuctions();

    Auction unsold = awaitAuctionState(auction.id(), Auction.State.UNSOLD);
    assertThat(unsold.finalBidId()).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sales WHERE auction_id = ?", Integer.class, auction.id()))
        .isZero();
  }

  @Test
  void restartPreservesRepeatedPersistedLateBidExtensions() throws Exception {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));
    UUID firstBidderId = activeAccount("first_bidder");
    UUID secondBidderId = activeAccount("second_bidder");
    Instant originalEnd = auction.endsAt();
    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();

    clock.set(originalEnd.minusSeconds(60));
    mockMvc
        .perform(bidRequest(firstBidderId, auction.id(), 10_000))
        .andExpect(status().isCreated());
    clock.set(originalEnd);
    mockMvc
        .perform(bidRequest(secondBidderId, auction.id(), 11_000))
        .andExpect(status().isCreated());
    Instant extendedEnd = originalEnd.plusSeconds(120);

    NEW_CONTEXT_TIME.set(originalEnd);
    try (ConfigurableApplicationContext ignored = restartApplication()) {
      // Startup reconciliation must read the persisted extended deadline, not the original one.
    }

    Auction restarted = auctions.get(auction.id());
    assertThat(restarted.state()).isEqualTo(Auction.State.LIVE);
    assertThat(restarted.effectiveEndAt()).isEqualTo(extendedEnd);
  }

  @Test
  void rejectsRescheduleAndCancellationAtTheExactStartInstant() {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));

    clock.set(auction.startsAt());

    assertThatThrownBy(
            () ->
                auctions.updateEditableTerms(
                    auction.sellerId(),
                    auction.id(),
                    null,
                    auction.startsAt().plusSeconds(300),
                    auction.endsAt().plusSeconds(300)))
        .isInstanceOf(AuctionApiException.class);
    assertThatThrownBy(
            () -> auctions.cancel(auction.sellerId(), auction.id(), "Too late to cancel."))
        .isInstanceOf(AuctionApiException.class);
  }

  @Test
  void scheduledSuspensionPreventsStartAndAdministratorReleasesItToDraft() {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));
    UUID moderatorId = UUID.randomUUID();
    UUID administratorId = UUID.randomUUID();

    auctions.suspend(
        AuctionOperator.moderator(moderatorId),
        auction.id(),
        SuspensionReasonCategory.POLICY_REVIEW,
        "The listing requires an operational review.",
        "Check the provenance document.");
    NEW_CONTEXT_TIME.set(auction.startsAt());
    try (ConfigurableApplicationContext ignored = restartApplication()) {
      // Startup reconciliation must leave the suspended auction frozen.
    }

    Auction suspended = auctions.get(auction.id());
    assertThat(suspended.state()).isEqualTo(Auction.State.SUSPENDED);
    assertThat(suspended.suspensionSourceState()).isEqualTo(Auction.State.SCHEDULED);

    auctions.release(AuctionOperator.administrator(administratorId), auction.id());

    Auction released = auctions.get(auction.id());
    assertThat(released.state()).isEqualTo(Auction.State.DRAFT);
    assertThat(released.timeline())
        .extracting(AuctionTimelineEntry::type)
        .containsExactly(
            AuctionTimelineEntry.Type.SCHEDULED,
            AuctionTimelineEntry.Type.SUSPENDED,
            AuctionTimelineEntry.Type.RELEASED);
  }

  @Test
  void liveSuspensionStoresExactRemainingDurationAndResumeUsesServerTime() throws Exception {
    Auction auction = scheduleAt(INITIAL_TIME.plusSeconds(300), INITIAL_TIME.plusSeconds(900));
    clock.set(auction.startsAt());
    lifecycle.reconcileDueAuctions();
    clock.set(auction.endsAt().minusSeconds(137));

    auctions.suspend(
        AuctionOperator.moderator(UUID.randomUUID()),
        auction.id(),
        SuspensionReasonCategory.SECURITY,
        "Bidding is paused for a security review.",
        null);

    Auction suspended = auctions.get(auction.id());
    assertThat(suspended.remainingDuration()).isEqualTo(java.time.Duration.ofSeconds(137));
    assertThatThrownBy(() -> auctionBidding.lockOpenAuction(auction.id()))
        .isInstanceOf(AuctionBidding.BidUnavailable.class);
    awaitAudit("AUCTION_SUSPENDED", auction.id());
    String suspensionMetadata =
        jdbcTemplate.queryForObject(
            "SELECT metadata FROM audit_records WHERE action = 'AUCTION_SUSPENDED' AND target_id = ?",
            String.class,
            auction.id());
    assertThat(suspensionMetadata)
        .contains(
            "reasonCategory=SECURITY", "publicReason=Bidding is paused for a security review.");
    Instant resumedAt = auction.endsAt().plusSeconds(600);
    NEW_CONTEXT_TIME.set(resumedAt);
    try (ConfigurableApplicationContext ignored = restartApplication()) {
      // Restart reconciliation must preserve the live suspension and remaining duration.
    }
    assertThat(auctions.get(auction.id()).state()).isEqualTo(Auction.State.SUSPENDED);

    clock.set(resumedAt);
    auctions.resume(AuctionOperator.administrator(UUID.randomUUID()), auction.id());

    Auction resumed = auctions.get(auction.id());
    assertThat(resumed.state()).isEqualTo(Auction.State.LIVE);
    assertThat(resumed.effectiveEndAt()).isEqualTo(resumedAt.plusSeconds(137));
    awaitAudit("AUCTION_RESUMED", auction.id());
  }

  private ConfigurableApplicationContext restartApplication() {
    return new SpringApplicationBuilder(
            CollectorsAuctionPlatformApplication.class,
            ClockConfiguration.class,
            TestMailConfiguration.class)
        .profiles("test")
        .web(WebApplicationType.SERVLET)
        .run(
            "--server.port=0",
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword(),
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--platform.auctions.reconciliation-delay-ms=60000");
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bidRequest(
      UUID bidderId, UUID auctionId, long amountCents) {
    return post("/api/v1/auctions/{id}/bids", auctionId)
        .with(user(bidderId.toString()).authorities(new SimpleGrantedAuthority("TRADING_ELIGIBLE")))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"amountCents\":"
                + amountCents
                + ",\"idempotencyKey\":\""
                + UUID.randomUUID()
                + "\"}");
  }

  private void reconcileWhenReleased(CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent closing workers were not released.");
      }
      lifecycle.reconcileDueAuctions();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Concurrent closing worker was interrupted.", exception);
    }
  }

  private org.springframework.test.web.servlet.MvcResult bidWhenReleased(
      CountDownLatch ready, CountDownLatch start, UUID bidderId, UUID auctionId) {
    ready.countDown();
    try {
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent bid and closing commands were not released.");
      }
      return mockMvc.perform(bidRequest(bidderId, auctionId, 10_000)).andReturn();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Concurrent bid command was interrupted.", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("Concurrent bid command failed.", exception);
    }
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
    assertThat(count).isGreaterThan(0);
  }

  private Auction awaitAuctionState(UUID auctionId, Auction.State expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    Auction auction = auctions.get(auctionId);
    while (System.nanoTime() < deadline) {
      auction = auctions.get(auctionId);
      if (auction.state() == expected) {
        return auction;
      }
      Thread.sleep(25);
    }
    assertThat(auction.state()).isEqualTo(expected);
    return auction;
  }

  private void awaitSaleCount(UUID auctionId, int expectedCount) throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    Integer count = 0;
    while (System.nanoTime() < deadline) {
      count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM sales WHERE auction_id = ?", Integer.class, auctionId);
      if (count != null && count == expectedCount) {
        return;
      }
      Thread.sleep(25);
    }
    assertThat(count).isEqualTo(expectedCount);
  }

  private Auction scheduleAt(Instant startsAt, Instant endsAt) {
    return scheduleAt(startsAt, endsAt, null);
  }

  private Auction scheduleAt(Instant startsAt, Instant endsAt, Long reserveAmountCents) {
    UUID sellerId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
            (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, 'unused', 'ACTIVE', ?, ?)
        """,
        sellerId,
        sellerId + "@example.com",
        "seller_" + sellerId.toString().substring(0, 8),
        Timestamp.from(INITIAL_TIME),
        Timestamp.from(INITIAL_TIME));
    jdbcTemplate.update(
        """
        INSERT INTO collectible_items
            (id, owner_id, category, title, description, condition, condition_notes,
             ownership_declared, status, created_at, updated_at)
        VALUES (?, ?, 'CARDS', 'Clock card', 'A clock-controlled auction fixture.',
                'EXCELLENT', 'No visible wear.', TRUE, 'APPROVED', ?, ?)
        """,
        itemId,
        sellerId,
        Timestamp.from(INITIAL_TIME),
        Timestamp.from(INITIAL_TIME));
    return auctions.schedule(
        sellerId, new ScheduleAuction(itemId, 10_000, 1_000, reserveAmountCents, startsAt, endsAt));
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

  @TestConfiguration
  static class ClockConfiguration {

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(NEW_CONTEXT_TIME.get());
    }
  }

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
      assertThat(messages).isNotEmpty();
      return messages.getLast();
    }

    List<MimeMessage> awaitMessages(int count) throws InterruptedException {
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
      while (messages.size() < count && System.nanoTime() < deadline) {
        Thread.sleep(25);
      }
      assertThat(messages).hasSizeGreaterThanOrEqualTo(count);
      return List.copyOf(messages);
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
