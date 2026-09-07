package io.github.klisman99.collectorsauctionplatform.catalog;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
interface CollectibleItemRepository extends JpaRepository<CollectibleItem, UUID> { List<CollectibleItem> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId); @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<CollectibleItem> findByIdAndOwnerId(UUID id, UUID ownerId); }
interface CollectibleItemMediaRepository extends JpaRepository<CollectibleItemMedia, UUID> { List<CollectibleItemMedia> findAllByItemIdOrderBySortOrder(UUID itemId); }
