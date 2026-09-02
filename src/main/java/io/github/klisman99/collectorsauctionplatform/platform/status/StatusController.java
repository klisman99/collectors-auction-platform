package io.github.klisman99.collectorsauctionplatform.platform.status;

import java.time.Clock;
import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/status", produces = MediaType.APPLICATION_JSON_VALUE)
class StatusController {

    private final Clock clock;

    StatusController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    @Operation(operationId = "getPlatformStatus", summary = "Read the public platform status")
    PlatformStatusResponse status() {
        return new PlatformStatusResponse("Collectors Auction Platform", "operational", Instant.now(clock));
    }

    @Schema(name = "PlatformStatus", description = "Current operational state of the application.")
    record PlatformStatusResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp) {
    }
}
