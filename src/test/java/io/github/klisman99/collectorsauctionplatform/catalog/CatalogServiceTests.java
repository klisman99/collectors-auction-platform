package io.github.klisman99.collectorsauctionplatform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CatalogServiceTests {

  @Test
  void refusesToResubmitAnApprovedCollectible() {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    CollectibleItem item = item(ownerId);
    item.submit(Instant.now());
    item.approve(Instant.now());

    CollectibleItemRepository items = mock(CollectibleItemRepository.class);
    when(items.findByIdAndOwnerId(itemId, ownerId)).thenReturn(Optional.of(item));
    CatalogService catalog =
        new CatalogService(
            items,
            mock(CollectibleItemMediaRepository.class),
            mock(CatalogImageStorage.class),
            mock(ApplicationEventPublisher.class));

    assertThatThrownBy(() -> catalog.submit(ownerId, itemId))
        .isInstanceOf(CatalogApiException.class)
        .hasMessageContaining("Only a draft");
  }

  @Test
  void invalidatesApprovalWhenTheOwnerReordersImages() {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    CollectibleItem item = item(ownerId);
    item.submit(Instant.now());
    item.approve(Instant.now());

    CollectibleItemRepository items = mock(CollectibleItemRepository.class);
    CollectibleItemMediaRepository media = mock(CollectibleItemMediaRepository.class);
    when(items.findByIdAndOwnerId(itemId, ownerId)).thenReturn(Optional.of(item));
    when(media.findAllByItemIdOrderBySortOrder(itemId)).thenReturn(List.of());
    CatalogService catalog =
        new CatalogService(
            items, media, mock(CatalogImageStorage.class), mock(ApplicationEventPublisher.class));

    catalog.reorder(ownerId, itemId, List.of());

    assertThat(item.status()).isEqualTo(CollectibleItem.Status.DRAFT);
  }

  @Test
  void keepsSubmittedImagesReadOnlyWhenTheirOrderChanges() {
    UUID ownerId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    CollectibleItem item = item(ownerId);
    item.submit(Instant.now());

    CollectibleItemRepository items = mock(CollectibleItemRepository.class);
    when(items.findByIdAndOwnerId(itemId, ownerId)).thenReturn(Optional.of(item));
    CatalogService catalog =
        new CatalogService(
            items,
            mock(CollectibleItemMediaRepository.class),
            mock(CatalogImageStorage.class),
            mock(ApplicationEventPublisher.class));

    assertThatThrownBy(() -> catalog.reorder(ownerId, itemId, List.of()))
        .isInstanceOf(CatalogApiException.class)
        .hasMessageContaining("read-only");
  }

  private CollectibleItem item(UUID ownerId) {
    return CollectibleItem.create(
        ownerId,
        new DraftRequest(
            CollectibleItem.Category.CARDS,
            null,
            "A complete collectible title",
            "A complete collectible description that contains enough detail.",
            CollectibleItem.Condition.EXCELLENT,
            "Condition notes are sufficiently detailed.",
            true),
        Instant.now());
  }
}
