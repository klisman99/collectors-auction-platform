package io.github.klisman99.collectorsauctionplatform.platform.openapi;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owns the OpenAPI document generated from the currently exposed HTTP contract.
 *
 * <p>The first slice deliberately has a compact hand-built generator rather than a static checked-in
 * specification. Its output is consumed by the frontend generator in {@code web/scripts/generate-openapi.mjs}.
 */
@RestController
class OpenApiController {

    @GetMapping(path = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> document() {
        Map<String, Object> statusSchema = new LinkedHashMap<>();
        statusSchema.put("type", "object");
        statusSchema.put("required", new String[] {"service", "status", "timestamp"});
        statusSchema.put("properties", Map.of(
                "service", Map.of("type", "string", "example", "Collectors Auction Platform"),
                "status", Map.of("type", "string", "example", "operational"),
                "timestamp", Map.of("type", "string", "format", "date-time")));

        return Map.of(
                "openapi", "3.1.1",
                "info", Map.of("title", "Collectors Auction Platform API", "version", "v1"),
                "paths", Map.of("/api/v1/status", Map.of("get", Map.of(
                        "operationId", "getPlatformStatus",
                        "summary", "Read the public platform status",
                        "responses", Map.of("200", Map.of(
                                "description", "The platform status.",
                                "content", Map.of("application/json", Map.of("schema", Map.of(
                                        "$ref", "#/components/schemas/PlatformStatus")))))))),
                "components", Map.of("schemas", Map.of("PlatformStatus", statusSchema)));
    }
}
