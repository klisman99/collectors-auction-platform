package io.github.klisman99.collectorsauctionplatform.bidding;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/operations/auctions/{auctionId}/bids",
    produces = MediaType.APPLICATION_JSON_VALUE)
class BiddingOperationsController {

  private final BiddingService bidding;

  BiddingOperationsController(BiddingService bidding) {
    this.bidding = bidding;
  }

  @GetMapping
  @Operation(
      operationId = "listOperationalBidHistory",
      summary = "List attributed bid history for operational review")
  List<BiddingService.OperationalBid> history(
      @PathVariable UUID auctionId, Authentication authentication) {
    return bidding.operationalHistory(auctionId, isAdministrator(authentication));
  }

  private boolean isAdministrator(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMINISTRATOR"));
  }
}
