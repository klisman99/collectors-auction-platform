package io.github.klisman99.collectorsauctionplatform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ImageStorageHealthIndicatorTests {

    @Test
    void reportsTheManagedImageStoreAsAvailable() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ImageStorageProperties properties = new ImageStorageProperties(
                URI.create("http://minio.test:9000"), "access-key", "secret-key", "collectors-images");
        RestClient client = restClientBuilder.baseUrl(properties.endpoint().toString()).build();
        ImageStorageHealthIndicator indicator = new ImageStorageHealthIndicator(client, properties);

        server.expect(once(), requestTo("http://minio.test:9000/minio/health/live"))
                .andRespond(withSuccess());

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        server.verify();
    }
}
