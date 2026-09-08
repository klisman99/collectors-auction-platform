package io.github.klisman99.collectorsauctionplatform.catalog;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("imageStorageHealthIndicator")
@ConditionalOnEnabledHealthIndicator("image-storage")
class ImageStorageHealthIndicator implements HealthIndicator {

  private final MinioClient client;
  private final String bucket;

  ImageStorageHealthIndicator(MinioClient client, ImageStorageProperties properties) {
    this.client = client;
    this.bucket = properties.bucket();
  }

  @Override
  public Health health() {
    try {
      boolean available = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());

      if (available) {
        return Health.up().withDetail("bucket", bucket).build();
      }
      return Health.down().withDetail("bucket", bucket).build();
    } catch (Exception exception) {
      return Health.down(exception).withDetail("bucket", bucket).build();
    }
  }
}
