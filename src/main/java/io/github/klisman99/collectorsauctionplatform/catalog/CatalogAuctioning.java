package io.github.klisman99.collectorsauctionplatform.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deliberate catalog interface used by auction and settlement lifecycle commands. */
@Service
public class CatalogAuctioning {

  private final CollectibleItemRepository items;
  private final CollectibleItemMediaRepository media;
  private final CatalogImageStorage storage;

  CatalogAuctioning(
      CollectibleItemRepository items,
      CollectibleItemMediaRepository media,
      CatalogImageStorage storage) {
    this.items = items;
    this.media = media;
    this.storage = storage;
  }

  @Transactional
  public ItemSnapshot lockApprovedItem(UUID ownerId, UUID itemId, Instant now) {
    CollectibleItem item =
        items.findByIdAndOwnerId(itemId, ownerId).orElseThrow(ItemNotAuctionable::notFound);
    if (item.status() != CollectibleItem.Status.APPROVED) {
      throw ItemNotAuctionable.notApproved();
    }
    if (item.isAuctionLocked()) {
      throw ItemNotAuctionable.locked();
    }
    item.lockForAuction(now);
    List<SnapshotMedia> snapshotMedia =
        media.findAllByItemIdOrderBySortOrder(itemId).stream()
            .map(
                image ->
                    new SnapshotMedia(
                        image.id(),
                        image.contentType(),
                        image.sortOrder(),
                        storage.get(image.displayStorageKey())))
            .toList();
    return new ItemSnapshot(
        item.id(),
        item.category().name(),
        item.otherCategoryLabel(),
        item.title(),
        item.description(),
        item.condition().name(),
        item.conditionNotes(),
        item.ownershipDeclared(),
        snapshotMedia);
  }

  @Transactional
  public void releaseUnchangedItem(UUID itemId, Instant now) {
    release(itemId, now);
  }

  /** Releases an unchanged approved item after a terminal failed settlement. */
  @Transactional
  public void releaseApprovedItemAfterFailedSettlement(UUID itemId, Instant now) {
    release(itemId, now);
  }

  /** Permanently archives the approved item after a completed settlement. */
  @Transactional
  public void archiveApprovedItemAfterCompletedSettlement(UUID itemId, Instant now) {
    CollectibleItem item = approvedItem(itemId);
    item.archiveAfterCompletedSettlement(now);
  }

  @Transactional
  public void releaseApprovedItemAfterAdministrativeCancellation(UUID itemId, Instant now) {
    release(itemId, now);
  }

  @Transactional
  public void revokeApprovalAfterAdministrativeCancellation(UUID itemId, Instant now) {
    CollectibleItem item = release(itemId, now);
    item.invalidateApproval(now);
  }

  private CollectibleItem release(UUID itemId, Instant now) {
    CollectibleItem item = approvedItem(itemId);
    item.releaseAfterTerminalAuction(now);
    return item;
  }

  private CollectibleItem approvedItem(UUID itemId) {
    return items
        .findByIdAndStatus(itemId, CollectibleItem.Status.APPROVED)
        .orElseThrow(ItemNotAuctionable::notApproved);
  }

  public record ItemSnapshot(
      UUID itemId,
      String category,
      String otherCategoryLabel,
      String title,
      String description,
      String condition,
      String conditionNotes,
      boolean ownershipDeclared,
      List<SnapshotMedia> media) {}

  public record SnapshotMedia(UUID mediaId, String contentType, int sortOrder, byte[] content) {}

  public static final class ItemNotAuctionable extends RuntimeException {
    private final Reason reason;

    private ItemNotAuctionable(Reason reason) {
      this.reason = reason;
    }

    public Reason reason() {
      return reason;
    }

    static ItemNotAuctionable notFound() {
      return new ItemNotAuctionable(Reason.NOT_FOUND);
    }

    static ItemNotAuctionable notApproved() {
      return new ItemNotAuctionable(Reason.NOT_APPROVED);
    }

    static ItemNotAuctionable locked() {
      return new ItemNotAuctionable(Reason.LOCKED);
    }

    public enum Reason {
      NOT_FOUND,
      NOT_APPROVED,
      LOCKED
    }
  }
}
