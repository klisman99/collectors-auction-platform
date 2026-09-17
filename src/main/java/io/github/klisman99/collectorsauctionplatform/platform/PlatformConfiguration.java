package io.github.klisman99.collectorsauctionplatform.platform;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
class PlatformConfiguration {

  @Bean
  Clock applicationClock() {
    return Clock.systemUTC();
  }

  @Bean
  SimpleAsyncTaskExecutor taskExecutor() {
    var executor = new SimpleAsyncTaskExecutor("application-events-");
    executor.setTaskTerminationTimeout(10_000);
    return executor;
  }

  @Bean
  OpenAPI platformOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Collectors Auction Platform API")
                .version("v1")
                .description("Same-origin API for the collectors auction platform."));
  }
}
