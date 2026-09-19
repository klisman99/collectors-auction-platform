package io.github.klisman99.collectorsauctionplatform.catalog;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

record DraftRequest(
    CollectibleItem.Category category,
    String otherCategoryLabel,
    String title,
    String description,
    CollectibleItem.Condition condition,
    String conditionNotes,
    boolean ownershipDeclared) {}

@Service
class CatalogService {

  private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
  private static final int MAX_IMAGES_PER_DRAFT = 5;

  private final CollectibleItemRepository items;
  private final CollectibleItemMediaRepository media;
  private final CatalogImageStorage storage;
  private final ApplicationEventPublisher events;

  CatalogService(
      CollectibleItemRepository items,
      CollectibleItemMediaRepository media,
      CatalogImageStorage storage,
      ApplicationEventPublisher events) {
    this.items = items;
    this.media = media;
    this.storage = storage;
    this.events = events;
  }

  @Transactional(readOnly = true)
  List<CollectibleItem> list(UUID ownerId) {
    return items.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
  }

  @Transactional(readOnly = true)
  CollectibleItem get(UUID ownerId, UUID itemId) {
    return owned(ownerId, itemId);
  }

  @Transactional
  CollectibleItem create(UUID ownerId, DraftRequest request) {
    validate(request);
    return items.save(CollectibleItem.create(ownerId, request, Instant.now()));
  }

  @Transactional
  CollectibleItem update(UUID ownerId, UUID itemId, DraftRequest request) {
    CollectibleItem item = ownedForUpdate(ownerId, itemId);
    assertNotAuctionLocked(item);
    assertNotArchived(item);
    if (item.status() == CollectibleItem.Status.UNDER_REVIEW) {
      throw CatalogApiException.cannotSubmit(
          "A submitted item is read-only until moderation decides it.");
    }
    item.invalidateApproval(Instant.now());
    validate(request);
    item.apply(request, Instant.now());
    return items.save(item);
  }

  @Transactional
  void delete(UUID ownerId, UUID itemId) {
    CollectibleItem item = ownedForUpdate(ownerId, itemId);
    assertNotAuctionLocked(item);
    assertNotArchived(item);
    if (item.status() == CollectibleItem.Status.UNDER_REVIEW
        || item.status() == CollectibleItem.Status.APPROVED) {
      throw CatalogApiException.cannotSubmit("A submitted item cannot be deleted.");
    }
    List<CollectibleItemMedia> images = media.findAllByItemIdOrderBySortOrder(itemId);

    items.delete(item);
    afterCommit(() -> images.forEach(this::deleteRenditions));
  }

  @Transactional
  CollectibleItemMedia addImage(UUID ownerId, UUID itemId, MultipartFile file) {
    CollectibleItem item = ownedForUpdate(ownerId, itemId);
    assertNotAuctionLocked(item);
    assertNotArchived(item);
    if (item.status() == CollectibleItem.Status.UNDER_REVIEW) {
      throw CatalogApiException.cannotSubmit(
          "A submitted item is read-only until moderation decides it.");
    }
    item.invalidateApproval(Instant.now());
    validateImageSize(file);

    List<CollectibleItemMedia> existingImages = media.findAllByItemIdOrderBySortOrder(itemId);
    if (existingImages.size() >= MAX_IMAGES_PER_DRAFT) {
      throw CatalogApiException.invalidImageCount("A draft can contain at most five images.");
    }

    try {
      byte[] source = file.getBytes();
      CatalogImageProcessor.ImageFormat format = CatalogImageProcessor.imageFormat(source);
      assertDeclaredImageTypeMatches(file, format);
      CatalogImageProcessor.Renditions renditions = CatalogImageProcessor.process(source);

      UUID mediaId = UUID.randomUUID();
      String baseStorageKey = item.id() + "/" + mediaId;
      String displayStorageKey = baseStorageKey + "/display.jpg";
      String thumbnailStorageKey = baseStorageKey + "/thumbnail.jpg";

      storage.put(displayStorageKey, renditions.display(), "image/jpeg");
      try {
        storage.put(thumbnailStorageKey, renditions.thumbnail(), "image/jpeg");
      } catch (RuntimeException exception) {
        storage.delete(displayStorageKey);
        throw exception;
      }

      afterRollback(
          () -> {
            storage.delete(displayStorageKey);
            storage.delete(thumbnailStorageKey);
          });
      return media.save(
          CollectibleItemMedia.create(
              itemId,
              displayStorageKey,
              thumbnailStorageKey,
              "image/jpeg",
              renditions.display().length,
              renditions.displayWidth(),
              renditions.displayHeight(),
              existingImages.size(),
              Instant.now()));
    } catch (IOException exception) {
      throw CatalogApiException.invalidImage("The image could not be processed safely.");
    }
  }

  @Transactional
  void reorder(UUID ownerId, UUID itemId, List<UUID> mediaIdsInOrder) {
    CollectibleItem item = ownedForUpdate(ownerId, itemId);
    assertNotAuctionLocked(item);
    assertNotArchived(item);
    if (item.status() == CollectibleItem.Status.UNDER_REVIEW) {
      throw CatalogApiException.cannotSubmit(
          "A submitted item is read-only until moderation decides it.");
    }
    item.invalidateApproval(Instant.now());
    List<CollectibleItemMedia> images = media.findAllByItemIdOrderBySortOrder(itemId);
    assertCompleteImageOrder(mediaIdsInOrder, images);

    for (int index = 0; index < images.size(); index++) {
      images.get(index).sortOrder(-1 - index);
    }
    media.flush();

    Map<UUID, CollectibleItemMedia> imageById = new HashMap<>();
    images.forEach(image -> imageById.put(image.id(), image));
    for (int index = 0; index < mediaIdsInOrder.size(); index++) {
      imageById.get(mediaIdsInOrder.get(index)).sortOrder(index);
    }
  }

  byte[] readDisplay(UUID ownerId, UUID itemId, UUID mediaId) {
    return storage.get(mediaForOwner(ownerId, itemId, mediaId).displayStorageKey());
  }

  byte[] readThumbnail(UUID ownerId, UUID itemId, UUID mediaId) {
    return storage.get(mediaForOwner(ownerId, itemId, mediaId).thumbnailStorageKey());
  }

  String mediaType(UUID ownerId, UUID itemId, UUID mediaId) {
    return mediaForOwner(ownerId, itemId, mediaId).contentType();
  }

  List<CollectibleItemMedia> images(UUID itemId) {
    return media.findAllByItemIdOrderBySortOrder(itemId);
  }

  @Transactional
  CollectibleItem submit(UUID ownerId, UUID itemId) {
    CollectibleItem item = ownedForUpdate(ownerId, itemId);
    if (item.status() != CollectibleItem.Status.DRAFT) {
      throw CatalogApiException.cannotSubmit("Only a draft can be submitted for moderation.");
    }
    if (media.countByItemId(itemId) < 1) {
      throw CatalogApiException.cannotSubmit("Submission requires at least one processed image.");
    }
    item.submit(Instant.now());
    events.publishEvent(
        new CollectibleSubmitted(item.id(), ownerId, item.title(), item.submittedAt()));
    return item;
  }

  private void validateImageSize(MultipartFile file) {
    if (file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
      throw CatalogApiException.invalidImageCount(
          "Images must be non-empty and no larger than 5 MB.");
    }
  }

  private void assertNotAuctionLocked(CollectibleItem item) {
    if (item.isAuctionLocked()) {
      throw CatalogApiException.auctionLocked();
    }
  }

  private void assertNotArchived(CollectibleItem item) {
    if (item.status() == CollectibleItem.Status.ARCHIVED) {
      throw CatalogApiException.archived();
    }
  }

  private void assertDeclaredImageTypeMatches(
      MultipartFile file, CatalogImageProcessor.ImageFormat format) {

    String declaredContentType =
        Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(java.util.Locale.ROOT);
    if (!declaredContentType.equals(format.contentType())) {
      throw CatalogApiException.invalidImage(
          "The declared image type does not match the image bytes.");
    }
  }

  private void assertCompleteImageOrder(
      List<UUID> mediaIdsInOrder, List<CollectibleItemMedia> images) {

    boolean hasEveryImageExactlyOnce =
        mediaIdsInOrder != null
            && mediaIdsInOrder.size() == images.size()
            && new HashSet<>(mediaIdsInOrder).size() == mediaIdsInOrder.size()
            && images.stream()
                .map(CollectibleItemMedia::id)
                .collect(Collectors.toSet())
                .equals(new HashSet<>(mediaIdsInOrder));
    if (!hasEveryImageExactlyOnce) {
      throw CatalogApiException.invalidImageOrder(
          "The image order must contain every draft image exactly once.");
    }
  }

  private CollectibleItem owned(UUID ownerId, UUID itemId) {
    return items
        .findById(itemId)
        .filter(item -> item.ownerId().equals(ownerId))
        .orElseThrow(CatalogApiException::notFound);
  }

  private CollectibleItem ownedForUpdate(UUID ownerId, UUID itemId) {
    return items.findByIdAndOwnerId(itemId, ownerId).orElseThrow(CatalogApiException::notFound);
  }

  private CollectibleItemMedia mediaForOwner(UUID ownerId, UUID itemId, UUID mediaId) {
    owned(ownerId, itemId);
    return media
        .findById(mediaId)
        .filter(image -> image.itemId().equals(itemId))
        .orElseThrow(CatalogApiException::notFound);
  }

  private void deleteRenditions(CollectibleItemMedia image) {
    storage.delete(image.displayStorageKey());
    storage.delete(image.thumbnailStorageKey());
  }

  private static void afterRollback(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              action.run();
            }
          }
        });
  }

  private static void afterCommit(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  private void validate(DraftRequest request) {
    if (request.category() == null
        || request.title() == null
        || request.title().trim().length() < 5
        || request.title().length() > 120) {
      throw CatalogApiException.invalid("Title must contain 5 to 120 characters.");
    }
    if (request.description() == null
        || request.description().trim().length() < 20
        || request.description().length() > 5_000) {
      throw CatalogApiException.invalid("Description must contain 20 to 5000 characters.");
    }
    if (request.condition() == null
        || request.conditionNotes() == null
        || request.conditionNotes().trim().length() < 10
        || request.conditionNotes().length() > 2_000) {
      throw CatalogApiException.invalid("Condition and condition notes are required.");
    }
    if (!request.ownershipDeclared()) {
      throw CatalogApiException.ownershipNotDeclared(
          "You must affirm that you own this physical collectible and have the right to sell it.");
    }
    if (request.category() == CollectibleItem.Category.OTHER
        && (request.otherCategoryLabel() == null
            || request.otherCategoryLabel().trim().isEmpty()
            || request.otherCategoryLabel().length() > 80)) {
      throw CatalogApiException.otherCategory("Other category requires a short label.");
    }
    if (request.category() != CollectibleItem.Category.OTHER
        && request.otherCategoryLabel() != null) {
      throw CatalogApiException.otherCategory("Only the Other category accepts a category label.");
    }
  }
}
