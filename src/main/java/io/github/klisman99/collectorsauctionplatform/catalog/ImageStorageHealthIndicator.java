package io.github.klisman99.collectorsauctionplatform.catalog;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component("imageStorageHealthIndicator")
@ConditionalOnEnabledHealthIndicator("image-storage")
class ImageStorageHealthIndicator implements HealthIndicator {

    private final RestClient client;
    private final String bucket;

    ImageStorageHealthIndicator(
            @Qualifier("imageStorageClient") RestClient client,
            ImageStorageProperties properties) {
        this.client = client;
        this.bucket = properties.bucket();
    }

    @Override
    public Health health() {
        try {
            boolean available = client.get()
                    .uri("/minio/health/live")
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();

            if (available) {
                return Health.up().withDetail("bucket", bucket).build();
            }
            return Health.down().withDetail("bucket", bucket).build();
        } catch (RestClientException exception) {
            return Health.down(exception).withDetail("bucket", bucket).build();
        }
    }
}
