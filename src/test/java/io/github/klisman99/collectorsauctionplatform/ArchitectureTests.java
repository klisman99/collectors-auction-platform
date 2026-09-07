package io.github.klisman99.collectorsauctionplatform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTests {

  @Test
  void documentedBusinessModulesRespectTheirBoundaries() {
    ApplicationModules modules = ApplicationModules.of(CollectorsAuctionPlatformApplication.class);

    assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
        .containsExactlyInAnyOrder(
            "identity",
            "catalog",
            "moderation",
            "auctions",
            "bidding",
            "settlement",
            "notifications",
            "audit",
            "platform");

    modules.verify();
  }
}
