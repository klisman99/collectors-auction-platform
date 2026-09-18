package io.github.klisman99.collectorsauctionplatform.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  SettlementHttpIntegrationTests.ClockConfiguration.class,
  SettlementHttpIntegrationTests.TestMailConfiguration.class
})
class SettlementHttpIntegrationTests {

  private static final Instant INITIAL_TIME = Instant.parse("2026-09-18T12:00:00Z");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MutableClock clock;

  @Autowired private SettlementLifecycle lifecycle;

  @Autowired private RecordingMailSender mailSender;

  @BeforeEach
  void clearFixtures() {
    jdbcTemplate.update(
        "DELETE FROM sales WHERE buyer_handle = 'buyer_settlement' OR seller_handle = 'seller_settlement'");
    jdbcTemplate.update(
        "DELETE FROM regular_accounts WHERE normalized_email IN (?, ?)",
        "buyer_settlement@example.com",
        "seller_settlement@example.com");
    clock.set(INITIAL_TIME);
    mailSender.clear();
  }

  @Test
  void buyerCanReadAndSafelyRepeatPaymentForAnImmutableSale() throws Exception {
    SaleFixture sale = paymentPendingSale();

    mockMvc
        .perform(get("/api/v1/sales/mine").with(regularAccount(sale.buyerId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(sale.saleId().toString()))
        .andExpect(jsonPath("$[0].auctionId").value(sale.auctionId().toString()))
        .andExpect(jsonPath("$[0].item.title").value("Settlement clock card"))
        .andExpect(jsonPath("$[0].buyer.handle").value("buyer_settlement"))
        .andExpect(jsonPath("$[0].seller.handle").value("seller_settlement"))
        .andExpect(jsonPath("$[0].amountCents").value(12_000))
        .andExpect(jsonPath("$[0].state").value("PAYMENT_PENDING"))
        .andExpect(jsonPath("$[0].paymentDeadlineAt").value("2026-09-19T12:00:00Z"));

    mockMvc
        .perform(
            post("/api/v1/sales/{id}/payment", sale.saleId())
                .with(regularAccount(sale.buyerId()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SHIPMENT_PENDING"))
        .andExpect(jsonPath("$.paidAt").value("2026-09-18T12:00:00Z"))
        .andExpect(jsonPath("$.shipmentDeadlineAt").value("2026-09-21T12:00:00Z"));

    mockMvc
        .perform(
            post("/api/v1/sales/{id}/payment", sale.saleId())
                .with(regularAccount(sale.buyerId()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SHIPMENT_PENDING"))
        .andExpect(jsonPath("$.paidAt").value("2026-09-18T12:00:00Z"));
  }

  @Test
  void sellerRecordsNonEmptyShipmentAndTheBuyerCannotDoSo() throws Exception {
    SaleFixture sale = paymentPendingSale();
    mockMvc
        .perform(
            post("/api/v1/sales/{id}/payment", sale.saleId())
                .with(regularAccount(sale.buyerId()))
                .with(csrf()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/sales/{id}/shipment", sale.saleId())
                .with(regularAccount(sale.buyerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"Correios\",\"trackingReference\":\"BR123\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SHIPMENT_FORBIDDEN"));

    mockMvc
        .perform(
            post("/api/v1/sales/{id}/shipment", sale.saleId())
                .with(regularAccount(sale.sellerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\" \",\"trackingReference\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/v1/sales/{id}/shipment", sale.saleId())
                .with(regularAccount(sale.sellerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\" Correios \",\"trackingReference\":\" BR123 \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SHIPPED"))
        .andExpect(jsonPath("$.carrier").value("Correios"))
        .andExpect(jsonPath("$.trackingReference").value("BR123"));

    mockMvc
        .perform(
            post("/api/v1/sales/{id}/shipment", sale.saleId())
                .with(regularAccount(sale.sellerId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"Correios\",\"trackingReference\":\"BR123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SHIPPED"));

    mockMvc
        .perform(
            post("/api/v1/sales/{id}/payment", sale.saleId())
                .with(regularAccount(sale.buyerId()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SHIPPED"));
  }

  @Test
  void suspendedBuyerCanCompletePaymentForTheirExistingSale() throws Exception {
    SaleFixture sale = paymentPendingSale();
    jdbcTemplate.update(
        "UPDATE regular_accounts SET status = 'SUSPENDED' WHERE id = ?", sale.buyerId());

    mockMvc
        .perform(
            post("/api/v1/sales/{id}/payment", sale.saleId())
                .with(suspendedRegularAccount(sale.buyerId()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SHIPMENT_PENDING"));
  }

  @Test
  void expiryWorkerFailsAPaymentPendingSaleAtItsStoredDeadlineAndNotifiesParticipants()
      throws Exception {
    SaleFixture sale = paymentPendingSale();
    clock.set(INITIAL_TIME.plusSeconds(24 * 60 * 60));

    lifecycle.reconcileDueSales();

    mockMvc
        .perform(get("/api/v1/sales/mine").with(regularAccount(sale.buyerId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].state").value("FAILED"))
        .andExpect(jsonPath("$[0].failedAt").value("2026-09-19T12:00:00Z"));
    awaitAudit("SALE_PAYMENT_EXPIRED", sale.saleId());
    assertThat(mailSender.awaitMessages(2))
        .extracting(message -> message.getSubject())
        .contains("Payment deadline expired", "Payment deadline expired for your sale");
  }

  private SaleFixture paymentPendingSale() {
    UUID buyerId = account("buyer_settlement");
    UUID sellerId = account("seller_settlement");
    UUID saleId = UUID.randomUUID();
    UUID auctionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO sales
            (id, auction_id, item_id, item_title, seller_id, seller_handle, buyer_id, buyer_handle,
             amount_cents, state, created_at, payment_deadline_at)
        VALUES (?, ?, ?, 'Settlement clock card', ?, 'seller_settlement', ?, 'buyer_settlement',
                12000, 'PAYMENT_PENDING', ?, ?)
        """,
        saleId,
        auctionId,
        UUID.randomUUID(),
        sellerId,
        buyerId,
        Timestamp.from(INITIAL_TIME),
        Timestamp.from(INITIAL_TIME.plusSeconds(24 * 60 * 60)));
    return new SaleFixture(saleId, auctionId, buyerId, sellerId);
  }

  private UUID account(String handle) {
    UUID accountId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
            (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, 'unused', 'ACTIVE', ?, ?)
        """,
        accountId,
        handle + "@example.com",
        handle,
        Timestamp.from(INITIAL_TIME),
        Timestamp.from(INITIAL_TIME));
    return accountId;
  }

  private RequestPostProcessor regularAccount(UUID accountId) {
    return user(accountId.toString()).authorities(regularAccountAuthority());
  }

  private RequestPostProcessor suspendedRegularAccount(UUID accountId) {
    return user(accountId.toString())
        .authorities(regularAccountAuthority(), new SimpleGrantedAuthority("ACCOUNT_SUSPENDED"));
  }

  private SimpleGrantedAuthority regularAccountAuthority() {
    return new SimpleGrantedAuthority("ROLE_REGULAR_ACCOUNT");
  }

  private record SaleFixture(UUID saleId, UUID auctionId, UUID buyerId, UUID sellerId) {}

  private void awaitAudit(String action, UUID saleId) throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    Integer count = 0;
    while (System.nanoTime() < deadline) {
      count =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM audit_records WHERE action = ? AND target_id = ?",
              Integer.class,
              action,
              saleId);
      if (count != null && count > 0) {
        return;
      }
      Thread.sleep(25);
    }
    assertThat(count).isGreaterThan(0);
  }

  @TestConfiguration
  static class ClockConfiguration {

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(INITIAL_TIME);
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

    List<MimeMessage> awaitMessages(int count) throws InterruptedException, MessagingException {
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
