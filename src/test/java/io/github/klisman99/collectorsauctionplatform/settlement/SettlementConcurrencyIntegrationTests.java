package io.github.klisman99.collectorsauctionplatform.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(SettlementConcurrencyIntegrationTests.TestMailConfiguration.class)
@TestPropertySource(
    properties = {
      "platform.identity.initial-administrator.email=settlement-lock@example.com",
      "platform.identity.initial-administrator.password=settlement lock administrator password",
      "platform.auctions.reconciliation-delay-ms=600000",
      "platform.settlement.reconciliation-delay-ms=600000",
      "spring.session.jdbc.cleanup-cron=0 0 0 1 1 *"
    })
class SettlementConcurrencyIntegrationTests {

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

  @Autowired private SaleService sales;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearFixtures() {
    jdbcTemplate.update("DELETE FROM sales");
    jdbcTemplate.update("DELETE FROM collectible_items");
    jdbcTemplate.update("DELETE FROM regular_accounts");
  }

  @Test
  void concurrentDeliveryConfirmationsPersistOneTerminalDisposition() throws Exception {
    SaleFixture sale = shippedSale();
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Sale.State> first = executor.submit(() -> confirmAfter(start, sale));
      Future<Sale.State> second = executor.submit(() -> confirmAfter(start, sale));
      start.countDown();

      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsOnly(Sale.State.COMPLETED);
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT state FROM sales WHERE id = ?", String.class, sale.saleId()))
        .isEqualTo("COMPLETED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT terminal_reason FROM sales WHERE id = ?", String.class, sale.saleId()))
        .isEqualTo("BUYER_CONFIRMED_DELIVERY");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT item_disposition FROM sales WHERE id = ?", String.class, sale.saleId()))
        .isEqualTo("ARCHIVED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM collectible_items WHERE id = ?", String.class, sale.itemId()))
        .isEqualTo("ARCHIVED");
  }

  private Sale.State confirmAfter(CountDownLatch start, SaleFixture sale) {
    try {
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out while waiting to confirm delivery.");
      }
      return sales.confirmDelivery(sale.buyerId(), sale.saleId()).state();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private SaleFixture shippedSale() {
    Instant now = Instant.now();
    UUID sellerId = account("settlement_seller", now);
    UUID buyerId = account("settlement_buyer", now);
    UUID itemId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO collectible_items
            (id, owner_id, category, title, description, condition, condition_notes,
             ownership_declared, status, created_at, updated_at, auction_locked_at)
        VALUES (?, ?, 'CARDS', 'Concurrent settlement card', ?, 'EXCELLENT', ?, TRUE,
                'APPROVED', ?, ?, ?)
        """,
        itemId,
        sellerId,
        "A collectible that verifies concurrent delivery confirmation is safe.",
        "Excellent condition with full notes for the concurrency scenario.",
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
    UUID saleId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO sales
            (id, auction_id, item_id, item_title, seller_id, seller_handle, buyer_id, buyer_handle,
             amount_cents, state, created_at, payment_deadline_at, paid_at, shipment_deadline_at,
             shipped_at, delivery_confirmation_deadline_at, carrier, tracking_reference)
        VALUES (?, ?, ?, 'Concurrent settlement card', ?, 'settlement_seller', ?, 'settlement_buyer',
                12000, 'SHIPPED', ?, ?, ?, ?, ?, ?, 'Correios', 'BR123')
        """,
        saleId,
        UUID.randomUUID(),
        itemId,
        sellerId,
        buyerId,
        Timestamp.from(now.minusSeconds(5 * 24 * 60 * 60)),
        Timestamp.from(now.minusSeconds(4 * 24 * 60 * 60)),
        Timestamp.from(now.minusSeconds(4 * 24 * 60 * 60)),
        Timestamp.from(now.minusSeconds(24 * 60 * 60)),
        Timestamp.from(now.minusSeconds(24 * 60 * 60)),
        Timestamp.from(now.plusSeconds(6 * 24 * 60 * 60)));
    return new SaleFixture(saleId, itemId, buyerId);
  }

  private UUID account(String handle, Instant now) {
    UUID accountId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO regular_accounts
            (id, normalized_email, public_handle, password_hash, status, registered_at, verified_at)
        VALUES (?, ?, ?, 'unused', 'ACTIVE', ?, ?)
        """,
        accountId,
        accountId + "@example.com",
        handle,
        Timestamp.from(now),
        Timestamp.from(now));
    return accountId;
  }

  private record SaleFixture(UUID saleId, UUID itemId, UUID buyerId) {}

  @TestConfiguration
  static class TestMailConfiguration {

    @Bean
    @Primary
    NoOpMailSender noOpMailSender() {
      return new NoOpMailSender();
    }
  }

  static class NoOpMailSender extends JavaMailSenderImpl {

    @Override
    public void send(jakarta.mail.internet.MimeMessage message) {
      // The test asserts terminal persistence, not external SMTP delivery.
    }
  }
}
