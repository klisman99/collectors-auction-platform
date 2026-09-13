package io.github.klisman99.collectorsauctionplatform.bidding;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
    path = "/api/v1/auctions/{auctionId}/bids",
    produces = MediaType.APPLICATION_JSON_VALUE)
class BiddingController {

  private final BiddingService bidding;

  BiddingController(BiddingService bidding) {
    this.bidding = bidding;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(operationId = "placeBid", summary = "Place an idempotent bid on a live auction")
  ResponseEntity<BidCommandResult> place(
      Principal principal, @PathVariable UUID auctionId, @Valid @RequestBody BidRequest request) {
    BidCommandResult result =
        bidding.place(
            UUID.fromString(principal.getName()),
            auctionId,
            request.idempotencyKey(),
            request.amountCents());
    return switch (result.status()) {
      case ACCEPTED -> ResponseEntity.status(HttpStatus.CREATED).body(result);
      case DEDUPLICATED -> ResponseEntity.ok(result);
      default -> throw BiddingApiException.from(result);
    };
  }

  @GetMapping
  @Operation(operationId = "listPublicBids", summary = "Read pseudonymous public bid history")
  List<BiddingService.PublicBid> history(@PathVariable UUID auctionId) {
    return bidding.history(auctionId);
  }

  record BidRequest(@Min(1_000) @Max(100_000_000) long amountCents, @NotNull UUID idempotencyKey) {}
}
