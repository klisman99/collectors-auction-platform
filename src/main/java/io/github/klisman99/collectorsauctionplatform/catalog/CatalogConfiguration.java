package io.github.klisman99.collectorsauctionplatform.catalog;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
class CatalogConfiguration {

  @Bean
  S3Client s3Client(ImageStorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(properties.endpoint())
        .region(Region.of(properties.region()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build())
        .build();
  }

  @Bean
  CatalogImageStorage catalogImageStorage(S3Client s3Client, ImageStorageProperties properties) {
    return new S3CatalogImageStorage(s3Client, properties.bucket());
  }
}
