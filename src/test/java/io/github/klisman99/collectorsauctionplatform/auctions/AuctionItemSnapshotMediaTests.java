package io.github.klisman99.collectorsauctionplatform.auctions;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogAuctioning;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuctionItemSnapshotMediaTests {

  @Test
  void ownsAnImmutableCopyOfPublishedImageBytes() {
    byte[] source = {1, 2, 3};
    AuctionItemSnapshotMedia snapshot =
        AuctionItemSnapshotMedia.from(
            new CatalogAuctioning.SnapshotMedia(UUID.randomUUID(), "image/jpeg", 0, source));

    source[0] = 9;
    byte[] served = snapshot.content();
    served[1] = 9;

    assertThat(snapshot.content()).containsExactly(1, 2, 3);
  }
}
