package io.github.klisman99.collectorsauctionplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionCookieIntegrationTests {

  @LocalServerPort private int port;

  @Test
  void issuesSessionAndCsrfCookiesOverARealHttpConnection() {
    var response =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/csrf")
            .retrieve()
            .toEntity(String.class);

    List<String> cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
    assertThat(cookies)
        .anySatisfy(
            cookie ->
                assertThat(cookie)
                    .startsWith("JSESSIONID=")
                    .contains("; HttpOnly")
                    .contains("; SameSite=Lax"));
    assertThat(cookies).anySatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN="));
  }
}
