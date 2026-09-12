package io.github.klisman99.collectorsauctionplatform.auctions;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auctions", produces = MediaType.APPLICATION_JSON_VALUE)
class AuctionController {

  private final AuctionService auctions;

  AuctionController(AuctionService auctions) {
    this.auctions = auctions;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "scheduleAuction", summary = "Publish an approved collectible auction")
  AuctionResponse schedule(Principal principal, @Valid @RequestBody ScheduleRequest request) {
    Auction auction =
        auctions.schedule(
            UUID.fromString(principal.getName()),
            new ScheduleAuction(
                request.itemId(),
                request.openingAmountCents(),
                request.minimumIncrementCents(),
                request.reserveAmountCents(),
                request.startsAt(),
                request.endsAt()));
    return response(auction, true, false);
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getAuction", summary = "Read a published auction snapshot")
  AuctionResponse get(@PathVariable UUID id, Authentication authentication) {
    AuctionAccess access =
        auctions.getVisible(
            id, authenticatedAccountId(authentication), isAdministrator(authentication));
    return response(access.auction(), access.exactReserveVisible(), access.internalNotesVisible());
  }

  @GetMapping
  @Operation(operationId = "listAuctions", summary = "Browse public auction lifecycle views")
  AuctionPageResponse discover(
      @RequestParam(defaultValue = "SCHEDULED") String state,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    if (page < 0 || size < 1 || size > 50) {
      throw AuctionApiException.invalidDiscoveryView();
    }
    UUID viewerId = authenticatedAccountId(authentication);
    boolean administrator = isAdministrator(authentication);
    var result =
        auctions.discover(
            AuctionDiscoveryView.fromHttp(state), page, size, viewerId, administrator);
    List<AuctionResponse> content =
        result.stream()
            .map(
                access ->
                    response(
                        access.auction(),
                        access.exactReserveVisible(),
                        access.internalNotesVisible()))
            .toList();
    return new AuctionPageResponse(
        content,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @GetMapping("/{id}/images/{mediaId}")
  @Operation(
      operationId = "getAuctionImage",
      summary = "Read an image from the published item snapshot")
  ResponseEntity<byte[]> image(@PathVariable UUID id, @PathVariable UUID mediaId) {
    AuctionItemSnapshotMedia image = auctions.image(id, mediaId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.content());
  }

  @GetMapping("/mine")
  @Operation(
      operationId = "listMyScheduledAuctions",
      summary = "List the seller's editable auctions")
  List<AuctionResponse> mine(Principal principal) {
    return auctions.scheduledForSeller(UUID.fromString(principal.getName())).stream()
        .map(auction -> response(auction, true, false))
        .toList();
  }

  @PutMapping(path = "/{id}/terms", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "updateAuctionTerms",
      summary = "Reschedule or lower the reserve before auction start")
  AuctionResponse updateTerms(
      Principal principal,
      @PathVariable UUID id,
      @Valid @RequestBody EditableTermsRequest request) {
    Auction auction =
        auctions.updateEditableTerms(
            UUID.fromString(principal.getName()),
            id,
            request.reserveAmountCents(),
            request.startsAt(),
            request.endsAt());
    return response(auction, true, false);
  }

  @PostMapping(path = "/{id}/cancellation", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(operationId = "cancelAuction", summary = "Cancel a scheduled auction as its seller")
  AuctionResponse cancel(
      Principal principal, @PathVariable UUID id, @Valid @RequestBody CancellationRequest request) {
    return response(
        auctions.cancel(UUID.fromString(principal.getName()), id, request.publicReason()),
        true,
        false);
  }

  private AuctionResponse response(
      Auction auction, boolean includeReserve, boolean includeInternalNotes) {
    AuctionItemSnapshot item = auction.itemSnapshot();
    return new AuctionResponse(
        auction.id(),
        auction.itemId(),
        auction.sellerHandle(),
        auction.state().name(),
        auction.openingAmountCents(),
        auction.currentAmountCents(),
        auction.minimumIncrementCents(),
        includeReserve ? auction.reserveAmountCents() : null,
        auction.reserveMet(),
        auction.startsAt(),
        auction.endsAt(),
        auction.effectiveEndAt(),
        auction.scheduledAt(),
        auction.endedAt(),
        new ItemResponse(
            item.category(),
            item.otherCategoryLabel(),
            item.title(),
            item.description(),
            item.condition(),
            item.conditionNotes(),
            item.ownershipDeclared(),
            auction.snapshotMedia().stream()
                .map(
                    media ->
                        new MediaResponse(
                            media.mediaId(),
                            "/api/v1/auctions/" + auction.id() + "/images/" + media.mediaId(),
                            media.contentType(),
                            media.sortOrder()))
                .toList()),
        new PolicyResponse(
            auction.policySnapshot().auctionType().name(),
            auction.policySnapshot().currency().name(),
            auction.policySnapshot().minimumAmountCents(),
            auction.policySnapshot().maximumAmountCents(),
            auction.policySnapshot().minimumLeadSeconds(),
            auction.policySnapshot().minimumDurationSeconds(),
            auction.policySnapshot().maximumDurationSeconds(),
            auction.policySnapshot().protectionWindowSeconds()),
        auction.timeline().stream()
            .map(entry -> TimelineResponse.from(entry, includeInternalNotes))
            .toList(),
        List.of(),
        List.of());
  }

  private UUID authenticatedAccountId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    try {
      return UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private boolean isAdministrator(Authentication authentication) {
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMINISTRATOR"));
  }

  record ScheduleRequest(
      @NotNull UUID itemId,
      long openingAmountCents,
      long minimumIncrementCents,
      Long reserveAmountCents,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt) {}

  record EditableTermsRequest(
      Long reserveAmountCents, @NotNull Instant startsAt, @NotNull Instant endsAt) {}

  record CancellationRequest(@NotNull String publicReason) {}

  record AuctionResponse(
      UUID id,
      UUID itemId,
      String sellerHandle,
      String state,
      long openingAmountCents,
      long currentAmountCents,
      long minimumIncrementCents,
      Long reserveAmountCents,
      boolean reserveMet,
      Instant startsAt,
      Instant endsAt,
      Instant effectiveEndAt,
      Instant scheduledAt,
      Instant endedAt,
      ItemResponse item,
      PolicyResponse policy,
      List<TimelineResponse> timeline,
      List<Object> eligibleBidHistory,
      List<Object> disqualifications) {}

  record AuctionPageResponse(
      List<AuctionResponse> content, int page, int size, long totalElements, int totalPages) {}

  record ItemResponse(
      String category,
      String otherCategoryLabel,
      String title,
      String description,
      String condition,
      String conditionNotes,
      boolean ownershipDeclared,
      List<MediaResponse> media) {}

  record MediaResponse(UUID id, String url, String contentType, int sortOrder) {}

  record PolicyResponse(
      String auctionType,
      String currency,
      long minimumAmountCents,
      long maximumAmountCents,
      long minimumLeadSeconds,
      long minimumDurationSeconds,
      long maximumDurationSeconds,
      long protectionWindowSeconds) {}

  record TimelineResponse(
      String type,
      Instant occurredAt,
      String reasonCategory,
      String publicReason,
      String internalNote,
      String itemDisposition) {
    static TimelineResponse from(AuctionTimelineEntry entry, boolean includeInternalNotes) {
      return new TimelineResponse(
          entry.type().name(),
          entry.occurredAt(),
          entry.reasonCategory(),
          entry.publicReason(),
          includeInternalNotes ? entry.internalNote() : null,
          entry.itemDisposition());
    }
  }
}
