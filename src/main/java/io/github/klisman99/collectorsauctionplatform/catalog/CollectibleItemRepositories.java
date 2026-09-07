package io.github.klisman99.collectorsauctionplatform.catalog;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface CollectibleItemRepository extends JpaRepository<CollectibleItem, UUID> {

  List<CollectibleItem> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<CollectibleItem> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<CollectibleItem> findAllByStatusOrderBySubmittedAtAsc(CollectibleItem.Status status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<CollectibleItem> findByIdAndStatus(UUID id, CollectibleItem.Status status);
}

interface CollectibleItemMediaRepository extends JpaRepository<CollectibleItemMedia, UUID> {

  List<CollectibleItemMedia> findAllByItemIdOrderBySortOrder(UUID itemId);

  long countByItemId(UUID itemId);
}

interface CollectibleItemReviewRepository extends JpaRepository<CollectibleItemReview, UUID> {}
