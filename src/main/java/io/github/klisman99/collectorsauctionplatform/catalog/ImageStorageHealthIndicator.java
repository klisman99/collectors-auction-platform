package io.github.klisman99.collectorsauctionplatform.catalog;

import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Component("imageStorageHealthIndicator")
@ConditionalOnEnabledHealthIndicator("image-storage")
class ImageStorageHealthIndicator implements HealthIndicator {

  private final S3Client client;
  private final String bucket;

  ImageStorageHealthIndicator(S3Client client, ImageStorageProperties properties) {
    this.client = client;
    this.bucket = properties.bucket();
  }

  @Override
  public Health health() {
    try {
      client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
      return Health.up().withDetail("bucket", bucket).build();
    } catch (Exception exception) {
      return Health.down(exception).withDetail("bucket", bucket).build();
    }
  }
}
