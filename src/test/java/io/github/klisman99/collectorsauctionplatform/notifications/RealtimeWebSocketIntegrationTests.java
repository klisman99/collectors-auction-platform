package io.github.klisman99.collectorsauctionplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
  RealtimeWebSocketIntegrationTests.TestAuthenticationController.class,
  RealtimeWebSocketIntegrationTests.AuthenticatedSubscriptionProbe.class
})
class RealtimeWebSocketIntegrationTests {

  @LocalServerPort private int port;

  @Autowired private SimpMessagingTemplate messagingTemplate;

  @Autowired private AuthenticatedSubscriptionProbe authenticatedSubscriptionProbe;

  @BeforeEach
  void resetAuthenticatedSubscriptionProbe() {
    authenticatedSubscriptionProbe.reset();
  }

  @Test
  void anonymousClientsCanObserveTheSameAuctionTopic() throws Exception {
    String destination = "/topic/auctions/" + UUID.randomUUID();
    CountDownLatch firstReceived = new CountDownLatch(1);
    CountDownLatch secondReceived = new CountDownLatch(1);
    WebSocketStompClient firstClient = stompClient();
    WebSocketStompClient secondClient = stompClient();
    StompSession firstSession = connect(firstClient);
    StompSession secondSession = connect(secondClient);

    try {
      subscribe(firstSession, destination, firstReceived);
      subscribe(secondSession, destination, secondReceived);

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while ((firstReceived.getCount() > 0 || secondReceived.getCount() > 0)
          && System.nanoTime() < deadline) {
        messagingTemplate.convertAndSend(destination, "accepted-bid");
        Thread.sleep(20);
      }

      assertThat(firstReceived.getCount()).isZero();
      assertThat(secondReceived.getCount()).isZero();
    } finally {
      firstSession.disconnect();
      secondSession.disconnect();
      firstClient.stop();
      secondClient.stop();
    }
  }

  @Test
  void authenticatedHttpSessionIsAvailableToStompSubscriptions() throws Exception {
    var response =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/v1/test/authenticate-websocket")
            .retrieve()
            .toBodilessEntity();
    String sessionCookie =
        response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
            .filter(value -> value.startsWith("JSESSIONID="))
            .map(value -> value.substring(0, value.indexOf(';')))
            .findFirst()
            .orElseThrow();
    WebSocketStompClient client = stompClient();
    var handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.add(HttpHeaders.COOKIE, sessionCookie);
    StompSession session =
        client
            .connectAsync(
                URI.create("ws://localhost:" + port + "/ws"),
                handshakeHeaders,
                new StompHeaders(),
                new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

    try {
      subscribe(session, "/topic/auctions/" + UUID.randomUUID(), new CountDownLatch(1));

      assertThat(authenticatedSubscriptionProbe.awaitPrincipal()).isEqualTo("collector-39");
    } finally {
      session.disconnect();
      client.stop();
    }
  }

  private WebSocketStompClient stompClient() {
    return new WebSocketStompClient(new StandardWebSocketClient());
  }

  private StompSession connect(WebSocketStompClient client) throws Exception {
    StompSession session =
        client
            .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    return session;
  }

  private void subscribe(StompSession session, String destination, CountDownLatch received) {
    session.subscribe(
        destination,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            received.countDown();
          }
        });
  }

  @RestController
  static class TestAuthenticationController {

    private final SecurityContextRepository securityContexts;

    TestAuthenticationController(SecurityContextRepository securityContexts) {
      this.securityContexts = securityContexts;
    }

    @GetMapping("/api/v1/test/authenticate-websocket")
    void authenticate(HttpServletRequest request, HttpServletResponse response) {
      var authentication =
          UsernamePasswordAuthenticationToken.authenticated(
              "collector-39", null, List.of(new SimpleGrantedAuthority("TRADING_ELIGIBLE")));
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      securityContexts.saveContext(context, request, response);
    }
  }

  static class AuthenticatedSubscriptionProbe
      implements WebSocketMessageBrokerConfigurer, ChannelInterceptor {

    private final AtomicReference<Principal> principal = new AtomicReference<>();
    private volatile CountDownLatch observed = new CountDownLatch(1);

    @Override
    public void configureClientInboundChannel(
        org.springframework.messaging.simp.config.ChannelRegistration registration) {
      registration.interceptors(this);
    }

    @Override
    public org.springframework.messaging.Message<?> preSend(
        org.springframework.messaging.Message<?> message,
        org.springframework.messaging.MessageChannel channel) {
      if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders())
          == SimpMessageType.SUBSCRIBE) {
        principal.set(SimpMessageHeaderAccessor.getUser(message.getHeaders()));
        observed.countDown();
      }
      return message;
    }

    void reset() {
      principal.set(null);
      observed = new CountDownLatch(1);
    }

    String awaitPrincipal() throws InterruptedException {
      assertThat(observed.await(5, TimeUnit.SECONDS)).isTrue();
      return principal.get() == null ? null : principal.get().getName();
    }
  }
}
