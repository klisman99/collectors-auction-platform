package io.github.klisman99.collectorsauctionplatform.moderation;

import io.github.klisman99.collectorsauctionplatform.catalog.CatalogModeration;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
class ModerationController {

  private final ModerationService moderation;

  ModerationController(ModerationService moderation) {
    this.moderation = moderation;
  }

  @GetMapping
  @Operation(
      operationId = "listModerationSubmissions",
      summary = "List collectible submissions awaiting review")
  List<ModerationSubmissionResponse> list() {
    return moderation.queue().stream().map(this::response).toList();
  }

  @GetMapping("/{id}/images/{mediaId}")
  @Operation(
      operationId = "getModerationSubmissionImage",
      summary = "Read a private image while reviewing a collectible")
  ResponseEntity<byte[]> image(@PathVariable UUID id, @PathVariable UUID mediaId) {
    CatalogModeration.ImageContent image = moderation.image(id, mediaId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  @PostMapping("/{id}/approve")
  @Operation(
      operationId = "approveModerationSubmission",
      summary = "Approve a collectible submission")
  ModerationSubmissionResponse approve(@PathVariable UUID id, Principal principal) {
    return response(moderation.approve(id, accountId(principal)));
  }

  @PostMapping(path = "/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "rejectModerationSubmission",
      summary = "Reject a collectible submission")
  ModerationSubmissionResponse reject(
      @PathVariable UUID id,
      @Valid @RequestBody ReviewDecisionRequest decision,
      Principal principal) {
    return response(
        moderation.reject(
            id, accountId(principal), decision.publicReason(), decision.internalNote()));
  }

  private UUID accountId(Principal principal) {
    return UUID.fromString(principal.getName());
  }

  private ModerationSubmissionResponse response(CatalogModeration.Submission submission) {
    return new ModerationSubmissionResponse(
        submission.itemId(),
        submission.category(),
        submission.otherCategoryLabel(),
        submission.title(),
        submission.description(),
        submission.condition(),
        submission.conditionNotes(),
        submission.ownershipDeclared(),
        submission.submittedAt(),
        submission.images().stream()
            .map(
                image ->
                    new ModerationImageResponse(
                        image.id(),
                        "/api/v1/moderation/submissions/"
                            + submission.itemId()
                            + "/images/"
                            + image.id(),
                        image.contentType(),
                        image.sortOrder()))
            .toList());
  }
}

record ReviewDecisionRequest(
    @NotBlank @Size(min = 5, max = 2000) String publicReason,
    @Size(max = 2000) String internalNote) {}

record ModerationSubmissionResponse(
    UUID id,
    String category,
    String otherCategoryLabel,
    String title,
    String description,
    String condition,
    String conditionNotes,
    boolean ownershipDeclared,
    Instant submittedAt,
    List<ModerationImageResponse> images) {}

record ModerationImageResponse(UUID id, String url, String contentType, int sortOrder) {}
