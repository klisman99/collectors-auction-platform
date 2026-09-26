package io.github.klisman99.collectorsauctionplatform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

@Testcontainers
class S3CatalogImageStorageIntegrationTests {

  private static final String BUCKET = "collectors-images";

  @Container
  static final GenericContainer<?> seaweedfs =
      new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.47"))
          .withCommand("mini", "-dir=/data")
          .withEnv("AWS_ACCESS_KEY_ID", "collectors")
          .withEnv("AWS_SECRET_ACCESS_KEY", "collectors-local-secret")
          .withEnv("S3_BUCKET", BUCKET)
          .withExposedPorts(8333);

  @BeforeAll
  static void waitForBucket() throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (System.nanoTime() < deadline) {
      try (S3Client client = client(endpoint())) {
        client.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build());
        return;
      } catch (Exception exception) {
        Thread.sleep(500);
      }
    }
    throw new AssertionError("SeaweedFS did not create the private media bucket.");
  }

  @Test
  void storesReadsAndDeletesPrivateRenditions() throws Exception {
    try (S3Client client = client(endpoint())) {
      S3CatalogImageStorage storage = new S3CatalogImageStorage(client, BUCKET);
      byte[] rendition = "normalized image bytes".getBytes(StandardCharsets.UTF_8);
      String key = "item/media/thumbnail.jpg";

      storage.put(key, rendition, "image/jpeg");

      assertThat(storage.get(key)).isEqualTo(rendition);
      assertThat(
              client
                  .headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build())
                  .contentType())
          .isEqualTo("image/jpeg");
      try (HttpClient anonymous = HttpClient.newHttpClient()) {
        URI objectUrl = URI.create(endpoint() + "/" + BUCKET + "/" + key);
        HttpRequest request = HttpRequest.newBuilder(objectUrl).GET().build();
        assertThat(anonymous.send(request, HttpResponse.BodyHandlers.discarding()).statusCode())
            .isEqualTo(403);
      }
      storage.delete(key);
      assertThatThrownBy(() -> storage.get(key)).isInstanceOf(CatalogStorageException.class);
    }
  }

  @Test
  void reportsBucketHealthAndMissingOrUnavailableStorage() {
    try (S3Client client = client(endpoint())) {
      ImageStorageProperties properties =
          new ImageStorageProperties(
              endpoint(), "us-east-1", "collectors", "collectors-local-secret", true, BUCKET);
      assertThat(new ImageStorageHealthIndicator(client, properties).health().getStatus().getCode())
          .isEqualTo("UP");
      ImageStorageProperties missingBucket =
          new ImageStorageProperties(
              endpoint(),
              "us-east-1",
              "collectors",
              "collectors-local-secret",
              true,
              "missing-bucket");
      assertThat(
              new ImageStorageHealthIndicator(client, missingBucket).health().getStatus().getCode())
          .isEqualTo("DOWN");
      assertThatThrownBy(
              () ->
                  new S3CatalogImageStorage(client, "missing-bucket")
                      .put("item/media/display.jpg", new byte[] {1}, "image/jpeg"))
          .isInstanceOf(CatalogStorageException.class);
    }

    try (S3Client unavailable = client(URI.create("http://127.0.0.1:1"))) {
      assertThat(
              new ImageStorageHealthIndicator(
                      unavailable,
                      new ImageStorageProperties(
                          URI.create("http://127.0.0.1:1"),
                          "us-east-1",
                          "collectors",
                          "collectors-local-secret",
                          true,
                          BUCKET))
                  .health()
                  .getStatus()
                  .getCode())
          .isEqualTo("DOWN");
      assertThatThrownBy(() -> new S3CatalogImageStorage(unavailable, BUCKET).get("missing"))
          .isInstanceOf(CatalogStorageException.class);
    }
  }

  @Test
  void keepsObjectsAfterServerRestart() throws InterruptedException {
    String key = "restart-proof/display.jpg";
    byte[] rendition = "persistent rendition".getBytes(StandardCharsets.UTF_8);
    try (S3Client client = client(endpoint())) {
      new S3CatalogImageStorage(client, BUCKET).put(key, rendition, "image/jpeg");
    }

    DockerClientFactory.instance().client().restartContainerCmd(seaweedfs.getContainerId()).exec();

    long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (System.nanoTime() < deadline) {
      try (S3Client client = client(endpoint())) {
        assertThat(new S3CatalogImageStorage(client, BUCKET).get(key)).isEqualTo(rendition);
        return;
      } catch (CatalogStorageException exception) {
        Thread.sleep(500);
      }
    }
    throw new AssertionError("The S3 rendition did not survive a SeaweedFS restart.");
  }

  private static URI endpoint() {
    return URI.create("http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(8333));
  }

  private static S3Client client(URI endpoint) {
    return S3Client.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create("collectors", "collectors-local-secret")))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }
}
