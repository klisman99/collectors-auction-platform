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
    return response(auction, true);
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getAuction", summary = "Read a published auction snapshot")
  AuctionResponse get(@PathVariable UUID id, Authentication authentication) {
    Auction auction = auctions.get(id);
    return response(
        auction,
        auctions.maySeeExactReserve(
            auction, authenticatedAccountId(authentication), isAdministrator(authentication)));
  }

  @GetMapping
  @Operation(operationId = "listScheduledAuctions", summary = "List published scheduled auctions")
  List<AuctionResponse> scheduled(Authentication authentication) {
    return auctions.scheduled().stream()
        .map(
            auction ->
                response(
                    auction,
                    auctions.maySeeExactReserve(
                        auction,
                        authenticatedAccountId(authentication),
                        isAdministrator(authentication))))
        .toList();
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
    return response(auction, true);
  }

  private AuctionResponse response(Auction auction, boolean includeReserve) {
    AuctionItemSnapshot item = auction.itemSnapshot();
    return new AuctionResponse(
        auction.id(),
        auction.itemId(),
        auction.sellerHandle(),
        auction.state().name(),
        auction.openingAmountCents(),
        auction.minimumIncrementCents(),
        includeReserve ? auction.reserveAmountCents() : null,
        false,
        auction.startsAt(),
        auction.endsAt(),
        auction.scheduledAt(),
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
            auction.policySnapshot().protectionWindowSeconds()));
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

  record AuctionResponse(
      UUID id,
      UUID itemId,
      String sellerHandle,
      String state,
      long openingAmountCents,
      long minimumIncrementCents,
      Long reserveAmountCents,
      boolean reserveMet,
      Instant startsAt,
      Instant endsAt,
      Instant scheduledAt,
      ItemResponse item,
      PolicyResponse policy) {}

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
}
