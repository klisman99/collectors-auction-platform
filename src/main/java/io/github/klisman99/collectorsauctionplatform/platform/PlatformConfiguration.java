package io.github.klisman99.collectorsauctionplatform.platform;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PlatformConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
