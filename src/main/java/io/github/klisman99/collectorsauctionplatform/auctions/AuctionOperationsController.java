package io.github.klisman99.collectorsauctionplatform.auctions;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/operations/auctions", produces = MediaType.APPLICATION_JSON_VALUE)
class AuctionOperationsController {

  private final AuctionService auctions;

  AuctionOperationsController(AuctionService auctions) {
    this.auctions = auctions;
  }

  @GetMapping("/suspended")
  @Operation(
      operationId = "listSuspendedAuctions",
      summary = "List the suspension operations queue")
  List<SuspendedAuctionResponse> queue(Authentication authentication) {
    boolean administrator = isAdministrator(authentication);
    return auctions.suspendedQueue().stream()
        .map(auction -> SuspendedAuctionResponse.from(auction, administrator))
        .toList();
  }

  @PostMapping(path = "/{id}/suspension", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(operationId = "suspendAuction", summary = "Suspend a scheduled or live auction")
  SuspendedAuctionResponse suspend(
      Principal principal,
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody ReasonRequest request) {
    Auction auction =
        auctions.suspend(
            operator(principal, authentication),
            id,
            category(request.reasonCategory()),
            request.publicReason(),
            request.internalNote());
    return SuspendedAuctionResponse.from(auction, isAdministrator(authentication));
  }

  @PostMapping("/{id}/release")
  @Operation(operationId = "releaseSuspendedAuction", summary = "Release a scheduled suspension")
  SuspendedAuctionResponse release(
      Principal principal, Authentication authentication, @PathVariable UUID id) {
    return SuspendedAuctionResponse.from(
        auctions.release(operator(principal, authentication), id), true);
  }

  @PostMapping("/{id}/resume")
  @Operation(operationId = "resumeSuspendedAuction", summary = "Resume a live suspension")
  SuspendedAuctionResponse resume(
      Principal principal, Authentication authentication, @PathVariable UUID id) {
    return SuspendedAuctionResponse.from(
        auctions.resume(operator(principal, authentication), id), true);
  }

  @PostMapping(path = "/{id}/cancellation", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "administrativelyCancelAuction",
      summary = "Cancel a suspended auction with explicit item disposition")
  SuspendedAuctionResponse cancel(
      Principal principal,
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody CancellationRequest request) {
    Auction auction =
        auctions.administrativelyCancel(
            operator(principal, authentication),
            id,
            category(request.reasonCategory()),
            request.publicReason(),
            request.internalNote(),
            disposition(request.itemDisposition()));
    return SuspendedAuctionResponse.from(auction, true);
  }

  private AuctionOperator operator(Principal principal, Authentication authentication) {
    UUID accountId = UUID.fromString(principal.getName());
    return isAdministrator(authentication)
        ? AuctionOperator.administrator(accountId)
        : AuctionOperator.moderator(accountId);
  }

  private boolean isAdministrator(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMINISTRATOR"));
  }

  private SuspensionReasonCategory category(String value) {
    try {
      return SuspensionReasonCategory.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw AuctionApiException.invalidAdministrativeReason();
    }
  }

  private AdministrativeItemDisposition disposition(String value) {
    try {
      return AdministrativeItemDisposition.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw AuctionApiException.suspensionResolutionInvalid();
    }
  }

  record ReasonRequest(
      @NotNull String reasonCategory,
      @NotBlank @Size(max = 500) String publicReason,
      @Size(max = 2000) String internalNote) {}

  record CancellationRequest(
      @NotNull String reasonCategory,
      @NotBlank @Size(max = 500) String publicReason,
      @Size(max = 2000) String internalNote,
      @NotNull String itemDisposition) {}

  record SuspendedAuctionResponse(
      UUID id,
      String itemTitle,
      String sellerHandle,
      String state,
      String sourceState,
      Instant suspendedAt,
      Long remainingDurationMillis,
      Instant effectiveEndAt,
      List<OperationsTimelineResponse> timeline) {
    static SuspendedAuctionResponse from(Auction auction, boolean includeInternalNotes) {
      return new SuspendedAuctionResponse(
          auction.id(),
          auction.itemSnapshot().title(),
          auction.sellerHandle(),
          auction.state().name(),
          auction.suspensionSourceState() == null ? null : auction.suspensionSourceState().name(),
          auction.suspendedAt(),
          auction.remainingDuration() == null ? null : auction.remainingDuration().toMillis(),
          auction.effectiveEndAt(),
          auction.timeline().stream()
              .map(entry -> OperationsTimelineResponse.from(entry, includeInternalNotes))
              .toList());
    }
  }

  record OperationsTimelineResponse(
      String type,
      Instant occurredAt,
      String reasonCategory,
      String publicReason,
      String internalNote,
      String itemDisposition) {
    static OperationsTimelineResponse from(
        AuctionTimelineEntry entry, boolean includeInternalNotes) {
      return new OperationsTimelineResponse(
          entry.type().name(),
          entry.occurredAt(),
          entry.reasonCategory(),
          entry.publicReason(),
          includeInternalNotes ? entry.internalNote() : null,
          entry.itemDisposition());
    }
  }
}
