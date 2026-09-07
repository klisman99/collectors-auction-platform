package io.github.klisman99.collectorsauctionplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IdentityHttpIntegrationTests.TestMailConfiguration.class)
class IdentityHttpIntegrationTests {

  private static final Pattern VERIFICATION_TOKEN = Pattern.compile("verificationToken=([^\\s]+)");
  private static final Pattern RECOVERY_TOKEN = Pattern.compile("recoveryToken=([^\\s]+)");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private RecordingMailSender mailSender;

  @Autowired private IncompleteEventPublications incompleteEventPublications;

  @BeforeEach
  void clearSentMessages() {
    mailSender.clear();
  }

  @Test
  void registersDeliversVerificationAndCreatesAPersistedVerifiedSession() throws Exception {
    Csrf csrf = csrf();
    String password = "very secure passphrase";

    MvcResult registration =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .cookie(csrf.cookie())
                    .header("X-XSRF-TOKEN", csrf.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {"email":"Collector@Example.com ","publicHandle":"Collector_27","password":"%s"}
                                """
                            .formatted(password)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.publicHandle").value("collector_27"))
            .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
            .andExpect(
                content()
                    .string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(password))))
            .andReturn();

    MimeMessage verificationEmail = mailSender.awaitMessage();
    assertThat(verificationEmail.getAllRecipients()[0].toString())
        .isEqualTo("collector@example.com");
    assertThat(verificationEmail.getSubject())
        .isEqualTo("Verify your Collectors Auction Platform account");
    String token = verificationToken(verificationEmail.getContent().toString());

    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"token\":\"" + token + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicHandle").value("collector_27"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    MvcResult signIn =
        mockMvc
            .perform(
                post("/api/v1/auth/sign-in")
                    .cookie(csrf.cookie())
                    .header("X-XSRF-TOKEN", csrf.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {"email":"collector@example.com","password":"%s"}
                                """
                            .formatted(password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.canTrade").value(true))
            .andReturn();

    Cookie sessionCookie = sessionCookie(signIn);
    mockMvc
        .perform(get("/api/v1/auth/session").cookie(sessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicHandle").value("collector_27"))
        .andExpect(jsonPath("$.verified").value(true));

    assertThat(jdbcTemplate.queryForObject("select count(*) from spring_session", Integer.class))
        .isGreaterThan(0);
    await(
        () ->
            jdbcTemplate.queryForObject(
                    "select count(*) from audit_records where metadata = 'publicHandle=collector_27'",
                    Integer.class)
                == 1);
    assertThat(registration.getResponse().getContentAsString()).doesNotContain("token");
  }

  @Test
  void letsAnUnverifiedAccountSignInButPreventsStateChangingCommands() throws Exception {
    Csrf csrf = csrf();
    register(csrf, "unverified@example.com", "unverified_27", "an adequate password");

    MvcResult signIn =
        mockMvc
            .perform(
                post("/api/v1/auth/sign-in")
                    .cookie(csrf.cookie())
                    .header("X-XSRF-TOKEN", csrf.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {"email":"unverified@example.com","password":"an adequate password"}
                                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.canTrade").value(false))
            .andReturn();

    mockMvc
        .perform(
            post("/api/v1/a-future-trading-command")
                .cookie(sessionCookie(signIn), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void returnsTheSameCredentialErrorForUnknownAccountsAndWrongPasswordsAndLimitsAttempts()
      throws Exception {
    Csrf csrf = csrf();
    register(csrf, "known@example.com", "known_27", "the known password");

    signIn(csrf, "missing@example.com", "a wrong password", "198.51.100.27")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.detail").value("The email address or password is incorrect."));
    signIn(csrf, "known@example.com", "a wrong password", "198.51.100.27")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.detail").value("The email address or password is incorrect."));

    for (int attempt = 0; attempt < 4; attempt++) {
      signIn(csrf, "limited@example.com", "a wrong password", "203.0.113.27")
          .andExpect(status().isUnauthorized());
    }
    signIn(csrf, "limited@example.com", "a wrong password", "203.0.113.27")
        .andExpect(status().isUnauthorized());
    signIn(csrf, "limited@example.com", "a wrong password", "203.0.113.27")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.ruleId").value("BR-AUTH-015"));
  }

  @Test
  void rejectsExpiredOrPreviouslyConsumedVerificationTokensWithoutRevealingWhichConditionApplied()
      throws Exception {
    Csrf csrf = csrf();
    register(csrf, "replay@example.com", "replay_27", "a replay safe password");
    String token = verificationToken(mailSender.awaitMessage().getContent().toString());

    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"token\":\"" + token + "\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"token\":\"" + token + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"))
        .andExpect(jsonPath("$.ruleId").value("BR-AUTH-005"));
  }

  @Test
  void concurrentConsumptionOfOneVerificationTokenSucceedsExactlyOnce() throws Exception {
    Csrf csrf = csrf();
    register(csrf, "concurrent@example.com", "concurrent_27", "a concurrent safe password");
    String token = verificationToken(mailSender.awaitMessage().getContent().toString());

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Integer> first = executor.submit(() -> verify(csrf, token));
      Future<Integer> second = executor.submit(() -> verify(csrf, token));
      assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 400);
    }
  }

  @Test
  void forwardedForCannotBeUsedToEvadeLoginRateLimit() throws Exception {
    Csrf csrf = csrf();
    for (int attempt = 0; attempt < 5; attempt++) {
      signInWithForwardedFor(
              csrf,
              "forged@example.com",
              "wrong password",
              "203.0.113.27",
              "198.51.100." + (attempt + 1))
          .andExpect(status().isUnauthorized());
    }
    signInWithForwardedFor(
            csrf, "forged@example.com", "wrong password", "203.0.113.27", "198.51.100.250")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.ruleId").value("BR-AUTH-015"));
  }

  @Test
  void suspendedAccountRemainsVerifiedButCannotTrade() throws Exception {
    Csrf csrf = csrf();
    register(csrf, "suspended@example.com", "suspended_27", "a suspended safe password");
    String token = verificationToken(mailSender.awaitMessage().getContent().toString());
    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
        .andExpect(status().isOk());
    jdbcTemplate.update(
        "update regular_accounts set status = 'SUSPENDED' where normalized_email = ?",
        "suspended@example.com");

    signIn(csrf, "suspended@example.com", "a suspended safe password", "203.0.113.90")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(true))
        .andExpect(jsonPath("$.canTrade").value(false));
  }

  private int verify(Csrf csrf, String token) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void retainsAFailedVerificationNotificationForRetryAfterTheRegistrationCommits()
      throws Exception {
    Csrf csrf = csrf();
    mailSender.failNextDelivery();

    register(csrf, "retry@example.com", "retry_27", "a retry safe password");
    mailSender.awaitAttempt();

    await(
        () ->
            jdbcTemplate.queryForObject(
                    "select count(*) from event_publication where completion_date is null",
                    Integer.class)
                > 0);

    incompleteEventPublications.resubmitIncompletePublications(
        ResubmissionOptions.defaults().withMinAge(Duration.ZERO));

    mailSender.awaitMessage();
    await(
        () ->
            jdbcTemplate.queryForObject(
                    "select count(*) from event_publication where completion_date is not null",
                    Integer.class)
                > 0);
  }

  @Test
  void recoversPasswordWithASingleUseTokenAndRevokesEveryExistingSession() throws Exception {
    Csrf csrf = csrf();
    String oldPassword = "the original password";
    String newPassword = "the replacement password";
    register(csrf, "recovery@example.com", "recovery_28", oldPassword);
    String verificationToken = verificationToken(mailSender.awaitMessage().getContent().toString());
    verify(csrf, verificationToken);
    mailSender.clear();

    MvcResult firstSession =
        signIn(csrf, "recovery@example.com", oldPassword, "198.51.100.28").andReturn();
    MvcResult secondSession =
        signIn(csrf, "recovery@example.com", oldPassword, "198.51.100.29").andReturn();

    mockMvc
        .perform(
            post("/api/v1/auth/request-password-recovery")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Recovery@Example.com \"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("RECOVERY_REQUEST_RECEIVED"));

    MimeMessage recoveryEmail = mailSender.awaitMessage();
    assertThat(recoveryEmail.getAllRecipients()[0].toString()).isEqualTo("recovery@example.com");
    assertThat(recoveryEmail.getSubject())
        .isEqualTo("Reset your Collectors Auction Platform password");
    String recoveryToken = recoveryToken(recoveryEmail.getContent().toString());

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .cookie(csrf.cookie(), sessionCookie(firstSession))
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"token\":\"" + recoveryToken + "\",\"password\":\"" + newPassword + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PASSWORD_RESET"));

    mockMvc
        .perform(get("/api/v1/auth/session").cookie(sessionCookie(firstSession)))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/auth/session").cookie(sessionCookie(secondSession)))
        .andExpect(status().isUnauthorized());

    signIn(csrf, "recovery@example.com", oldPassword, "198.51.100.30")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    signIn(csrf, "recovery@example.com", newPassword, "198.51.100.31").andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"token\":\""
                        + recoveryToken
                        + "\",\"password\":\""
                        + "another replacement password"
                        + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_RECOVERY_TOKEN_INVALID"))
        .andExpect(jsonPath("$.ruleId").value("BR-AUTH-005"));
  }

  @Test
  void concurrentRecoveryRequestsLeaveExactlyOneUsableToken() throws Exception {
    Csrf csrf = csrf();
    String email = "concurrent-recovery-28@example.com";
    register(csrf, email, "concurrent_recovery_28", "a concurrent recovery password");
    verify(csrf, verificationToken(mailSender.awaitMessage().getContent().toString()));
    mailSender.clear();

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Integer> first =
          executor.submit(() -> requestPasswordRecovery(csrf, email, "198.51.100.50"));
      Future<Integer> second =
          executor.submit(() -> requestPasswordRecovery(csrf, email, "198.51.100.51"));
      assertThat(java.util.List.of(first.get(), second.get())).containsExactly(202, 202);
    }

    await(() -> mailSender.messages.size() == 2);
    String firstToken = recoveryToken(mailSender.messages.getFirst().getContent().toString());
    String secondToken = recoveryToken(mailSender.messages.getLast().getContent().toString());
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Integer> first =
          executor.submit(
              () -> resetPassword(csrf, firstToken, "a concurrent replacement password"));
      Future<Integer> second =
          executor.submit(
              () -> resetPassword(csrf, secondToken, "another concurrent replacement password"));
      assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 400);
    }
  }

  @Test
  void returnsTheSameAcceptedRecoveryResponseForUnknownAddresses() throws Exception {
    Csrf csrf = csrf();

    mockMvc
        .perform(
            post("/api/v1/auth/request-password-recovery")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing-28@example.com\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("RECOVERY_REQUEST_RECEIVED"));

    assertThat(mailSender.messages).isEmpty();
  }

  @Test
  void limitsPasswordRecoveryRequestsToThreePerAddressAndClientIpPerHour() throws Exception {
    Csrf csrf = csrf();
    for (int attempt = 0; attempt < 3; attempt++) {
      mockMvc
          .perform(
              post("/api/v1/auth/request-password-recovery")
                  .cookie(csrf.cookie())
                  .header("X-XSRF-TOKEN", csrf.token())
                  .with(
                      request -> {
                        request.setRemoteAddr("203.0.113.28");
                        return request;
                      })
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"limited-28@example.com\"}"))
          .andExpect(status().isAccepted());
    }

    mockMvc
        .perform(
            post("/api/v1/auth/request-password-recovery")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .with(
                    request -> {
                      request.setRemoteAddr("203.0.113.28");
                      return request;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"limited-28@example.com\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("PASSWORD_RECOVERY_RATE_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.ruleId").value("BR-AUTH-015"));
  }

  @Test
  void signsOutTheCurrentSessionAndCanRevokeAllSessions() throws Exception {
    Csrf csrf = csrf();
    String password = "a session management password";
    register(csrf, "sessions-28@example.com", "sessions_28", password);
    verify(csrf, verificationToken(mailSender.awaitMessage().getContent().toString()));

    MvcResult signedOut =
        signIn(csrf, "sessions-28@example.com", password, "198.51.100.40").andReturn();
    Cookie signedOutCookie = sessionCookie(signedOut);
    mockMvc
        .perform(
            post("/api/v1/auth/sign-out")
                .cookie(signedOutCookie, csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/auth/session").cookie(signedOutCookie))
        .andExpect(status().isUnauthorized());

    MvcResult firstSession =
        signIn(csrf, "sessions-28@example.com", password, "198.51.100.41").andReturn();
    MvcResult secondSession =
        signIn(csrf, "sessions-28@example.com", password, "198.51.100.42").andReturn();
    mockMvc
        .perform(
            post("/api/v1/auth/revoke-all-sessions")
                .cookie(sessionCookie(firstSession), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/auth/session").cookie(sessionCookie(firstSession)))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/auth/session").cookie(sessionCookie(secondSession)))
        .andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.ResultActions signIn(
      Csrf csrf, String email, String password, String clientIp) throws Exception {
    return signIn(csrf, email, password, clientIp, null);
  }

  private org.springframework.test.web.servlet.ResultActions signInWithForwardedFor(
      Csrf csrf, String email, String password, String clientIp, String forwardedFor)
      throws Exception {
    return signIn(csrf, email, password, clientIp, forwardedFor);
  }

  private org.springframework.test.web.servlet.ResultActions signIn(
      Csrf csrf, String email, String password, String clientIp, String forwardedFor)
      throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/v1/auth/sign-in")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .with(
                mockRequest -> {
                  mockRequest.setRemoteAddr(clientIp);
                  return mockRequest;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    if (forwardedFor != null) {
      request.header("X-Forwarded-For", forwardedFor);
    }
    return mockMvc.perform(request);
  }

  private int requestPasswordRecovery(Csrf csrf, String email, String clientIp) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/request-password-recovery")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .with(
                    request -> {
                      request.setRemoteAddr(clientIp);
                      return request;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private int resetPassword(Csrf csrf, String token, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private void register(Csrf csrf, String email, String publicHandle, String password)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"%s","publicHandle":"%s","password":"%s"}
                                """
                        .formatted(email, publicHandle, password)))
        .andExpect(status().isCreated());
  }

  private Csrf csrf() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/csrf")).andExpect(status().isOk()).andReturn();
    return new Csrf(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText(),
        result.getResponse().getCookie("XSRF-TOKEN"));
  }

  private String verificationToken(String body) {
    Matcher matcher = VERIFICATION_TOKEN.matcher(body);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private String recoveryToken(String body) {
    Matcher matcher = RECOVERY_TOKEN.matcher(body);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private Cookie sessionCookie(MvcResult result) {
    return java.util.Arrays.stream(result.getResponse().getCookies())
        .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Sign-in did not issue a session cookie."));
  }

  private void await(Condition condition) throws InterruptedException {
    awaitCondition(condition);
  }

  private static void awaitCondition(Condition condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.matches() && System.nanoTime() < deadline) {
      Thread.sleep(25);
    }
    assertThat(condition.matches()).isTrue();
  }

  private record Csrf(String token, Cookie cookie) {}

  @FunctionalInterface
  private interface Condition {
    boolean matches();
  }

  @TestConfiguration
  static class TestMailConfiguration {

    @Bean
    @Primary
    RecordingMailSender recordingMailSender() {
      return new RecordingMailSender();
    }
  }

  static class RecordingMailSender extends JavaMailSenderImpl {

    private final CopyOnWriteArrayList<MimeMessage> messages = new CopyOnWriteArrayList<>();
    private volatile boolean failNextDelivery;
    private volatile boolean attempted;

    @Override
    public void send(MimeMessage message) {
      attempted = true;
      if (failNextDelivery) {
        failNextDelivery = false;
        throw new MailSendException("Simulated Mailpit failure");
      }
      messages.add(message);
    }

    @Override
    public void send(MimeMessage... messages) {
      for (MimeMessage message : messages) {
        send(message);
      }
    }

    void clear() {
      messages.clear();
      failNextDelivery = false;
      attempted = false;
    }

    void failNextDelivery() {
      failNextDelivery = true;
    }

    MimeMessage awaitMessage() throws InterruptedException {
      await(() -> !messages.isEmpty());
      return messages.getFirst();
    }

    void awaitAttempt() throws InterruptedException {
      await(() -> attempted);
    }

    private void await(Condition condition) throws InterruptedException {
      awaitCondition(condition);
    }
  }
}
