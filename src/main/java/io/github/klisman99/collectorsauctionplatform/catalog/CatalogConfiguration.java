package io.github.klisman99.collectorsauctionplatform.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import io.minio.MinioClient;

@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
class CatalogConfiguration {

    @Bean
    MinioClient minioClient(ImageStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint().toString())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    CatalogImageStorage catalogImageStorage(MinioClient minioClient, ImageStorageProperties properties) {
        return new MinioCatalogImageStorage(minioClient, properties.bucket());
    }
}
