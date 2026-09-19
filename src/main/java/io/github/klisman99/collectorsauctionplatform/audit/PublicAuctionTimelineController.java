package io.github.klisman99.collectorsauctionplatform.audit;

import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auctions", produces = MediaType.APPLICATION_JSON_VALUE)
class PublicAuctionTimelineController {

  private final AuditQueryService auditHistory;

  PublicAuctionTimelineController(AuditQueryService auditHistory) {
    this.auditHistory = auditHistory;
  }

  @GetMapping("/{auctionId}/timeline")
  @Operation(operationId = "listPublicAuctionTimeline", summary = "List safe public auction facts")
  AuditHistoryPageResponse list(
      @PathVariable UUID auctionId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result = auditHistory.listPublicAuctionTimeline(auctionId, page, size);
    return new AuditHistoryPageResponse(
        result.map(AuditHistoryEventResponse::publicView).getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }
}
