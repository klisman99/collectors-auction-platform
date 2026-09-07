package io.github.klisman99.collectorsauctionplatform.catalog;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/moderation/submissions",
    produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMINISTRATOR')")
class ModerationController {

  private final CatalogService catalog;

  ModerationController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  @Operation(
      operationId = "listModerationSubmissions",
      summary = "List collectible submissions awaiting review")
  List<ModerationSubmission> list() {
    return catalog.reviewQueue().stream().map(this::response).toList();
  }

  @PostMapping("/{id}/approve")
  @Operation(
      operationId = "approveModerationSubmission",
      summary = "Approve a collectible submission")
  ModerationSubmission approve(@PathVariable UUID id, java.security.Principal principal) {
    return response(catalog.approve(id, accountId(principal)));
  }

  @PostMapping(path = "/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "rejectModerationSubmission",
      summary = "Reject a collectible submission")
  ModerationSubmission reject(
      @PathVariable UUID id,
      @RequestBody ReviewDecision decision,
      java.security.Principal principal) {
    return response(
        catalog.reject(id, accountId(principal), decision.publicReason(), decision.internalNote()));
  }

  private UUID accountId(java.security.Principal principal) {
    return UUID.fromString(principal.getName());
  }

  private ModerationSubmission response(CollectibleItem item) {
    return new ModerationSubmission(
        item.id(),
        item.ownerId(),
        item.title(),
        item.status(),
        item.submittedAt(),
        item.submissionReason());
  }
}

record ReviewDecision(String publicReason, String internalNote) {}

record ModerationSubmission(
    UUID id,
    UUID ownerId,
    String title,
    CollectibleItem.Status status,
    Instant submittedAt,
    String submissionReason) {}
