package io.github.klisman99.collectorsauctionplatform.audit;

import io.swagger.v3.oas.annotations.Operation;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/history", produces = MediaType.APPLICATION_JSON_VALUE)
class AccountHistoryController {

  private final AuditQueryService auditHistory;

  AccountHistoryController(AuditQueryService auditHistory) {
    this.auditHistory = auditHistory;
  }

  @GetMapping("/mine")
  @Operation(operationId = "listMyHistory", summary = "List the authenticated account's history")
  AuditHistoryPageResponse mine(
      Principal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result =
        auditHistory.listParticipantHistory(UUID.fromString(principal.getName()), page, size);
    return new AuditHistoryPageResponse(
        result.map(AuditHistoryEventResponse::participantView).getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }
}
