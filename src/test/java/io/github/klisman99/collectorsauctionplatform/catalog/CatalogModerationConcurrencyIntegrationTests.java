package io.github.klisman99.collectorsauctionplatform.catalog;

import static org.assertj.core.api.Assertions.assertThat;

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
      "platform.identity.initial-administrator.email=moderation-lock@example.com",
      "platform.identity.initial-administrator.password=moderation lock administrator password"
    })
class CatalogModerationConcurrencyIntegrationTests {

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

  @Autowired private CatalogModeration moderation;

  @Autowired private CollectibleItemRepository items;

  @BeforeEach
  void clearItems() {
    items.deleteAll();
  }

  @Test
  void persistsExactlyOneDecisionWhenReviewersDecideConcurrently() throws Exception {
    CollectibleItem item =
        items.saveAndFlush(
            CollectibleItem.create(
                UUID.randomUUID(),
                new DraftRequest(
                    CollectibleItem.Category.CARDS,
                    null,
                    "A complete collectible title",
                    "A complete collectible description that contains enough detail.",
                    CollectibleItem.Condition.EXCELLENT,
                    "Condition notes are sufficiently detailed.",
                    true),
                Instant.now()));
    item.submit(Instant.now());
    items.flush();

    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> approval =
          executor.submit(() -> decide(start, () -> moderation.approve(item.id())));
      Future<Boolean> rejection =
          executor.submit(() -> decide(start, () -> moderation.reject(item.id(), "Policy reason")));
      start.countDown();

      assertThat(List.of(approval.get(20, TimeUnit.SECONDS), rejection.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }

  private boolean decide(CountDownLatch start, Runnable command) {
    try {
      start.await(10, TimeUnit.SECONDS);
      command.run();
      return true;
    } catch (CatalogApiException exception) {
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
