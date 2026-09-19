package io.github.klisman99.collectorsauctionplatform.audit;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/operations/audit-records",
    produces = MediaType.APPLICATION_JSON_VALUE)
class OperationalAuditHistoryController {

  private final AuditQueryService auditHistory;

  OperationalAuditHistoryController(AuditQueryService auditHistory) {
    this.auditHistory = auditHistory;
  }

  @GetMapping
  @Operation(
      operationId = "listOperationalAuditHistory",
      summary = "List moderation and auction-suspension history")
  AuditHistoryPageResponse list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    var result = auditHistory.listModeratorHistory(page, size);
    return new AuditHistoryPageResponse(
        result.map(AuditHistoryEventResponse::moderatorView).getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }
}
