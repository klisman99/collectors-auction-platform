package io.github.klisman99.collectorsauctionplatform.platform.status;

import java.time.Clock;
import java.time.Instant;

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
    PlatformStatusResponse status() {
        return new PlatformStatusResponse("Collectors Auction Platform", "operational", Instant.now(clock));
    }

    record PlatformStatusResponse(String service, String status, Instant timestamp) {
    }
}
