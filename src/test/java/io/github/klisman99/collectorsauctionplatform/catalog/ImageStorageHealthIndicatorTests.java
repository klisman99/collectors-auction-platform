package io.github.klisman99.collectorsauctionplatform.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

class ImageStorageHealthIndicatorTests {
  @Test
  void reportsAnUnreachableManagedImageStoreAsDown() {
    ImageStorageProperties properties =
        new ImageStorageProperties(
            URI.create("http://127.0.0.1:1"),
            "us-east-1",
            "access-key",
            "secret-key",
            true,
            "collectors-images");
    try (S3Client client =
        S3Client.builder()
            .endpointOverride(properties.endpoint())
            .region(Region.of(properties.region()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()) {
      assertThat(new ImageStorageHealthIndicator(client, properties).health().getStatus())
          .isEqualTo(Status.DOWN);
    }
  }
}
