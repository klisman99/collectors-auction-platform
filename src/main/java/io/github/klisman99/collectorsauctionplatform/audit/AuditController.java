package io.github.klisman99.collectorsauctionplatform.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/admin/audit-records", produces = MediaType.APPLICATION_JSON_VALUE)
class AuditController {

    private final AuditQueryService auditQueryService;

    AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @Operation(operationId = "listAdministrativeAuditRecords", summary = "List the administrator audit summary")
    List<AuditResponse> list() {
        return auditQueryService.listAll().stream()
                .map(AuditResponse::from)
                .toList();
    }

    @Schema(name = "AuditRecord", description = "An administrator-visible immutable audit record.")
    record AuditResponse(
            UUID id,
            String actorType,
            UUID actorId,
            String action,
            String targetType,
            UUID targetId,
            Instant occurredAt,
            String metadata) {

        static AuditResponse from(AuditRecord record) {
            return new AuditResponse(
                    record.id(),
                    record.actorType().name(),
                    record.actorId(),
                    record.action().name(),
                    record.targetType().name(),
                    record.targetId(),
                    record.occurredAt(),
                    record.metadata());
        }
    }
}
