package io.github.klisman99.collectorsauctionplatform.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.MinioClient;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class ImageStorageHealthIndicatorTests {
    @Test
    void reportsAnUnreachableManagedImageStoreAsDown() {
        ImageStorageProperties properties = new ImageStorageProperties(URI.create("http://127.0.0.1:1"), "access-key", "secret-key", "collectors-images");
        MinioClient client = MinioClient.builder().endpoint(properties.endpoint().toString()).credentials(properties.accessKey(), properties.secretKey()).build();
        assertThat(new ImageStorageHealthIndicator(client, properties).health().getStatus()).isEqualTo(Status.DOWN);
    }
}
