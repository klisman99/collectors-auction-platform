package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/admin/operational-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
class OperationalAccountController {

    private final OperationalAccountService service;

    OperationalAccountController(OperationalAccountService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "inviteOperationalAccount", summary = "Invite a dedicated moderator or administrator")
    OperationalAccountResponse invite(
            @Valid @RequestBody InviteOperationalAccountRequest request,
            Authentication authentication) {
        OperationalAccountService.InvitationResult result = service.invite(
                accountId(authentication),
                request.email(),
                request.role(),
                request.reasonCategory(),
                request.publicReason(),
                request.internalNote());
        return OperationalAccountResponse.from(result);
    }

    @GetMapping
    @Operation(operationId = "listOperationalAccounts", summary = "List operational accounts for administrators")
    List<OperationalAccountService.OperationalAccountView> list() {
        return service.list();
    }

    @PostMapping(path = "/{accountId}/deactivate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deactivateOperationalAccount", summary = "Permanently deactivate an operational account")
    void deactivate(
            @PathVariable UUID accountId,
            @Valid @RequestBody DeactivateOperationalAccountRequest request,
            Authentication authentication) {
        service.deactivate(
                accountId(authentication),
                accountId,
                request.reasonCategory(),
                request.publicReason(),
                request.internalNote());
    }

    private UUID accountId(Authentication authentication) {
        return ((AccountSessionPrincipal) authentication.getPrincipal()).accountId();
    }

    record InviteOperationalAccountRequest(
            @NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "MODERATOR|ADMINISTRATOR") String role,
            @NotBlank @Pattern(regexp = "STAFFING|SECURITY|ROLE_CHANGE|OTHER") String reasonCategory,
            @NotBlank @Size(max = 500) String publicReason,
            @Size(max = 2000) String internalNote) {

        InviteOperationalAccountRequest {
            email = email == null ? null : email.trim();
            role = role == null ? null : role.trim().toUpperCase(java.util.Locale.ROOT);
            reasonCategory = reasonCategory == null ? null : reasonCategory.trim().toUpperCase(java.util.Locale.ROOT);
            publicReason = publicReason == null ? null : publicReason.trim();
            internalNote = internalNote == null ? null : internalNote.trim();
        }
    }

    record DeactivateOperationalAccountRequest(
            @NotBlank @Pattern(regexp = "STAFFING|SECURITY|ROLE_CHANGE|OTHER") String reasonCategory,
            @NotBlank @Size(max = 500) String publicReason,
            @Size(max = 2000) String internalNote) {

        DeactivateOperationalAccountRequest {
            reasonCategory = reasonCategory == null ? null : reasonCategory.trim().toUpperCase(java.util.Locale.ROOT);
            publicReason = publicReason == null ? null : publicReason.trim();
            internalNote = internalNote == null ? null : internalNote.trim();
        }
    }

    @Schema(name = "OperationalAccount", description = "The non-trading operational account state.")
    record OperationalAccountResponse(UUID id, String email, String role, String status) {

        static OperationalAccountResponse from(OperationalAccountService.InvitationResult result) {
            return new OperationalAccountResponse(result.id(), result.email(), result.role(), result.status());
        }
    }

}
