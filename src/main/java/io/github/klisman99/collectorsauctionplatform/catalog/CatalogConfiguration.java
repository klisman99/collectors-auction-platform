package io.github.klisman99.collectorsauctionplatform.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
class CatalogConfiguration {

    @Bean
    RestClient imageStorageClient(ImageStorageProperties properties) {
        return RestClient.builder().baseUrl(properties.endpoint().toString()).build();
    }
}
