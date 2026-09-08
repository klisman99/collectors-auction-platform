package io.github.klisman99.collectorsauctionplatform.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deliberate catalog interface used by the moderation module. */
@Service
public class CatalogModeration {

  private final CollectibleItemRepository items;
  private final CollectibleItemMediaRepository media;
  private final CatalogImageStorage storage;

  CatalogModeration(
      CollectibleItemRepository items,
      CollectibleItemMediaRepository media,
      CatalogImageStorage storage) {
    this.items = items;
    this.media = media;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public List<Submission> submissionsAwaitingReview() {
    return items.findAllByStatusOrderBySubmittedAtAsc(CollectibleItem.Status.UNDER_REVIEW).stream()
        .map(this::submission)
        .toList();
  }

  @Transactional
  public Submission approve(UUID itemId) {
    CollectibleItem item = itemAwaitingReview(itemId);
    item.approve(Instant.now());
    return submission(item);
  }

  @Transactional
  public Submission reject(UUID itemId, String publicReason) {
    CollectibleItem item = itemAwaitingReview(itemId);
    item.reject(publicReason, Instant.now());
    return submission(item);
  }

  @Transactional(readOnly = true)
  public ImageContent reviewImage(UUID itemId, UUID mediaId) {
    items
        .findById(itemId)
        .filter(item -> item.status() == CollectibleItem.Status.UNDER_REVIEW)
        .orElseThrow(CatalogApiException::notFound);
    CollectibleItemMedia image =
        media
            .findById(mediaId)
            .filter(candidate -> candidate.itemId().equals(itemId))
            .orElseThrow(CatalogApiException::notFound);
    return new ImageContent(storage.get(image.displayStorageKey()), image.contentType());
  }

  private CollectibleItem itemAwaitingReview(UUID itemId) {
    return items
        .findByIdAndStatus(itemId, CollectibleItem.Status.UNDER_REVIEW)
        .orElseThrow(CatalogApiException::reviewConflict);
  }

  private Submission submission(CollectibleItem item) {
    List<Image> images =
        media.findAllByItemIdOrderBySortOrder(item.id()).stream()
            .map(image -> new Image(image.id(), image.contentType(), image.sortOrder()))
            .toList();
    return new Submission(
        item.id(),
        item.ownerId(),
        item.category().name(),
        item.otherCategoryLabel(),
        item.title(),
        item.description(),
        item.condition().name(),
        item.conditionNotes(),
        item.ownershipDeclared(),
        item.submittedAt(),
        images);
  }

  public record Submission(
      UUID itemId,
      UUID ownerId,
      String category,
      String otherCategoryLabel,
      String title,
      String description,
      String condition,
      String conditionNotes,
      boolean ownershipDeclared,
      Instant submittedAt,
      List<Image> images) {}

  public record Image(UUID id, String contentType, int sortOrder) {}

  public record ImageContent(byte[] bytes, String contentType) {}
}
