package io.github.klisman99.collectorsauctionplatform.accountadministration;

import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountAdministration;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountSuspensionReasonCategory;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/admin/regular-accounts",
    produces = MediaType.APPLICATION_JSON_VALUE)
class RegularAccountAdministrationController {

  private final AccountAdministrationService administration;

  RegularAccountAdministrationController(AccountAdministrationService administration) {
    this.administration = administration;
  }

  @GetMapping
  @Operation(
      operationId = "listRegularAccounts",
      summary = "List regular-account status for administrators")
  List<RegularAccountAdministration.RegularAccountView> list() {
    return administration.list();
  }

  @PostMapping(path = "/{accountId}/suspension", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "suspendRegularAccount",
      summary = "Suspend a regular account and permanently disqualify its eligible bids")
  RegularAccountAdministration.RegularAccountView suspend(
      Principal principal,
      @PathVariable UUID accountId,
      @Valid @RequestBody SuspensionRequest request) {
    return administration.suspend(
        UUID.fromString(principal.getName()),
        accountId,
        reasonCategory(request.reasonCategory()),
        request.publicReason(),
        request.internalNote());
  }

  @PostMapping(path = "/{accountId}/reactivation", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "reactivateRegularAccount",
      summary = "Reactivate a regular account without restoring disqualified bids")
  RegularAccountAdministration.RegularAccountView reactivate(
      Principal principal,
      @PathVariable UUID accountId,
      @Valid @RequestBody SuspensionRequest request) {
    return administration.reactivate(
        UUID.fromString(principal.getName()),
        accountId,
        reasonCategory(request.reasonCategory()),
        request.publicReason(),
        request.internalNote());
  }

  private RegularAccountSuspensionReasonCategory reasonCategory(String value) {
    try {
      return RegularAccountSuspensionReasonCategory.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw AccountAdministrationApiException.invalidSuspensionReason();
    }
  }

  record SuspensionRequest(
      @NotBlank @Pattern(regexp = "SECURITY|POLICY_VIOLATION|FRAUD|OTHER") String reasonCategory,
      @NotBlank @Size(max = 500) String publicReason,
      @Size(max = 2000) String internalNote) {

    SuspensionRequest {
      reasonCategory =
          reasonCategory == null ? null : reasonCategory.trim().toUpperCase(Locale.ROOT);
      publicReason = publicReason == null ? null : publicReason.trim();
      internalNote = internalNote == null || internalNote.isBlank() ? null : internalNote.trim();
    }
  }
}
