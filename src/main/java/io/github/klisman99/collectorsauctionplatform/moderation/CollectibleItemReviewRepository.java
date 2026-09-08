package io.github.klisman99.collectorsauctionplatform.moderation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CollectibleItemReviewRepository extends JpaRepository<CollectibleItemReview, UUID> {}
