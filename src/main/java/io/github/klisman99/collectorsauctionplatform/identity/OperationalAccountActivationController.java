package io.github.klisman99.collectorsauctionplatform.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
class OperationalAccountActivationController {

  private final OperationalAccountService service;

  OperationalAccountActivationController(OperationalAccountService service) {
    this.service = service;
  }

  @PostMapping(path = "/activate-operational-account", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "activateOperationalAccount",
      summary = "Activate an invited operational account")
  ActivationResponse activate(@Valid @RequestBody ActivateOperationalAccountRequest request) {
    OperationalAccountService.ActivationResult result =
        service.activate(request.token(), request.password());
    return new ActivationResponse(result.id(), result.email(), result.role(), result.status());
  }

  record ActivateOperationalAccountRequest(
      @NotBlank @Size(max = 128) String token, @NotNull String password) {}

  @Schema(
      name = "OperationalAccountActivation",
      description = "The result of consuming an operational activation invitation.")
  record ActivationResponse(java.util.UUID id, String email, String role, String status) {}
}
