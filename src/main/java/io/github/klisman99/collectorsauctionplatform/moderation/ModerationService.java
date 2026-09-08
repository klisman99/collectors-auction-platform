package io.github.klisman99.collectorsauctionplatform.moderation;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogModeration;
import io.github.klisman99.collectorsauctionplatform.identity.AccountDirectory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ModerationService {

  private final CatalogModeration catalog;
  private final AccountDirectory accounts;
  private final CollectibleItemReviewRepository reviews;
  private final ApplicationEventPublisher events;

  ModerationService(
      CatalogModeration catalog,
      AccountDirectory accounts,
      CollectibleItemReviewRepository reviews,
      ApplicationEventPublisher events) {
    this.catalog = catalog;
    this.accounts = accounts;
    this.reviews = reviews;
    this.events = events;
  }

  @Transactional(readOnly = true)
  List<CatalogModeration.Submission> queue() {
    return catalog.submissionsAwaitingReview();
  }

  @Transactional
  CatalogModeration.Submission approve(UUID itemId, UUID reviewerId) {
    CatalogModeration.Submission submission = catalog.approve(itemId);
    recordDecision(submission, reviewerId, ModerationDecisionMade.Decision.APPROVED, null, null);
    return submission;
  }

  @Transactional
  CatalogModeration.Submission reject(
      UUID itemId, UUID reviewerId, String publicReason, String internalNote) {
    String normalizedReason = publicReason.trim();
    CatalogModeration.Submission submission = catalog.reject(itemId, normalizedReason);
    recordDecision(
        submission,
        reviewerId,
        ModerationDecisionMade.Decision.REJECTED,
        normalizedReason,
        blankToNull(internalNote));
    return submission;
  }

  @Transactional(readOnly = true)
  CatalogModeration.ImageContent image(UUID itemId, UUID mediaId) {
    return catalog.reviewImage(itemId, mediaId);
  }

  private void recordDecision(
      CatalogModeration.Submission submission,
      UUID reviewerId,
      ModerationDecisionMade.Decision decision,
      String publicReason,
      String internalNote) {
    Instant now = Instant.now();
    reviews.save(
        CollectibleItemReview.create(
            submission.itemId(),
            reviewerId,
            CollectibleItemReview.Decision.valueOf(decision.name()),
            publicReason,
            internalNote,
            now));
    AccountDirectory.AccountContact owner = accounts.regularAccount(submission.ownerId());
    events.publishEvent(
        new ModerationDecisionMade(
            submission.itemId(),
            submission.ownerId(),
            reviewerId,
            owner.email(),
            submission.title(),
            decision,
            publicReason,
            now));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
