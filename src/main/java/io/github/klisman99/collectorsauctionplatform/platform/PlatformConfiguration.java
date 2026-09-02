package io.github.klisman99.collectorsauctionplatform.platform;

import java.time.Clock;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PlatformConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI platformOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Collectors Auction Platform API")
                .version("v1")
                .description("Same-origin API for the collectors auction platform."));
    }
}
