package io.github.klisman99.collectorsauctionplatform.catalog;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface CollectibleItemRepository extends JpaRepository<CollectibleItem, UUID> { List<CollectibleItem> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId); }
interface CollectibleItemMediaRepository extends JpaRepository<CollectibleItemMedia, UUID> { List<CollectibleItemMedia> findAllByItemIdOrderBySortOrder(UUID itemId); }
