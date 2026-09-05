package io.github.klisman99.collectorsauctionplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
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
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "platform.identity.initial-administrator.email=bootstrap-29@example.com",
        "platform.identity.initial-administrator.password=bootstrap administrator password"
})
@Import(OperationalAccountHttpIntegrationTests.TestMailConfiguration.class)
class OperationalAccountHttpIntegrationTests {

    private static final Pattern ACTIVATION_TOKEN = Pattern.compile("operationalActivationToken=([^\\s]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearMessages() {
        jdbcTemplate.update("DELETE FROM operational_account_invitations");
        jdbcTemplate.update("DELETE FROM operational_accounts WHERE normalized_email <> ?", "bootstrap-29@example.com");
        jdbcTemplate.update("DELETE FROM audit_records WHERE target_type = 'OPERATIONAL_ACCOUNT'");
        mailSender.clear();
    }

    @Test
    void administratorInvitesActivatesAndAuthenticatesADedicatedModerator() throws Exception {
        Csrf csrf = csrf();

        mockMvc.perform(get("/api/v1/admin/operational-accounts"))
                .andExpect(status().isUnauthorized());

        MvcResult administrator = signIn(csrf, "bootstrap-29@example.com", "bootstrap administrator password", "198.51.100.129")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("OPERATIONAL"))
                .andExpect(jsonPath("$.role").value("ADMINISTRATOR"))
                .andExpect(jsonPath("$.canTrade").value(false))
                .andReturn();

        mockMvc.perform(post("/api/v1/admin/operational-accounts")
                        .cookie(csrf.cookie(), sessionCookie(administrator))
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "moderator-29@example.com",
                                  "role": "MODERATOR",
                                  "reasonCategory": "STAFFING",
                                  "publicReason": "Cover evening moderation",
                                  "internalNote": "Invited for the M1 support rotation."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("moderator-29@example.com"))
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.status").value("INVITED"));

        MimeMessage invitation = mailSender.awaitMessage();
        assertThat(invitation.getAllRecipients()[0].toString()).isEqualTo("moderator-29@example.com");
        assertThat(invitation.getSubject()).isEqualTo("You are invited to Collectors Auction Platform operations");
        String token = activationToken(invitation.getContent().toString());

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"moderator-29@example.com","publicHandle":"regular_29","password":"regular account password"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        mockMvc.perform(post("/api/v1/auth/activate-operational-account")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"moderator activation password"}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("moderator-29@example.com"))
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        signIn(csrf, "moderator-29@example.com", "moderator activation password", "198.51.100.130")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("OPERATIONAL"))
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.canTrade").value(false));
    }

    @Test
    void administratorCanRevokePendingInvitationsAndExpiredInvitationsCannotActivate() throws Exception {
        Csrf csrf = csrf();
        MvcResult administrator = signIn(csrf, "bootstrap-29@example.com", "bootstrap administrator password", "198.51.100.137")
                .andExpect(status().isOk())
                .andReturn();

        String pendingAccountId = invite(csrf, administrator, "pending-29@example.com", "MODERATOR");
        String pendingToken = activationToken(mailSender.awaitMessage().getContent().toString());

        mockMvc.perform(post("/api/v1/admin/operational-accounts/" + pendingAccountId + "/deactivate")
                        .cookie(csrf.cookie(), sessionCookie(administrator))
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCategory":"SECURITY","publicReason":"Invitation no longer required"}
                                """))
                .andExpect(status().isNoContent());
        activateWithStatus(csrf, pendingToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OPERATIONAL_ACTIVATION_TOKEN_INVALID"));

        String expiredAccountId = invite(csrf, administrator, "expired-29@example.com", "MODERATOR");
        String expiredToken = activationToken(mailSender.awaitMessage().getContent().toString());
        jdbcTemplate.update("UPDATE operational_account_invitations SET expires_at = ? WHERE operational_account_id = ?",
                Instant.now().minusSeconds(1), UUID.fromString(expiredAccountId));
        activateWithStatus(csrf, expiredToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OPERATIONAL_ACTIVATION_TOKEN_INVALID"));
    }

    @Test
    void deactivationRevokesSessionsPreservesAttributionAndProtectsTheLastAdministrator() throws Exception {
        Csrf csrf = csrf();
        MvcResult administrator = signIn(csrf, "bootstrap-29@example.com", "bootstrap administrator password", "198.51.100.131")
                .andExpect(status().isOk())
                .andReturn();
        String administratorId = objectMapper.readTree(administrator.getResponse().getContentAsString())
                .get("accountId")
                .asText();

        String accountId = inviteAndActivate(csrf, administrator, "administrator-29@example.com", "ADMINISTRATOR");
        MvcResult targetSession = signIn(
                        csrf,
                        "administrator-29@example.com",
                        "administrator activation password",
                        "198.51.100.132")
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post("/api/v1/admin/operational-accounts/" + accountId + "/deactivate")
                        .cookie(csrf.cookie(), sessionCookie(administrator))
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonCategory": "SECURITY",
                                  "publicReason": "Access is no longer required",
                                  "internalNote": "Preserve this note for administrators only."
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie(targetSession)))
                .andExpect(status().isUnauthorized());
        signIn(csrf, "administrator-29@example.com", "administrator activation password", "198.51.100.133")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(get("/api/v1/admin/operational-accounts")
                        .cookie(csrf.cookie(), sessionCookie(administrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(accountId)).value("DEACTIVATED"));

        mockMvc.perform(get("/api/v1/admin/audit-records")
                        .cookie(csrf.cookie(), sessionCookie(administrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.targetId == '%s' && @.action == 'OPERATIONAL_ACCOUNT_DEACTIVATED')].actorId"
                        .formatted(accountId)).value(org.hamcrest.Matchers.hasItem(administratorId.toString())));

        mockMvc.perform(post("/api/v1/admin/operational-accounts/" + administratorId + "/deactivate")
                        .cookie(csrf.cookie(), sessionCookie(administrator))
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCategory":"SECURITY","publicReason":"Remove the final administrator"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMINISTRATOR_PROTECTED"))
                .andExpect(jsonPath("$.ruleId").value("BR-AUTH-014"));
    }

    @Test
    void operationalAccountsCannotUseTradingCommandsAndModeratorsCannotManageAccounts() throws Exception {
        Csrf csrf = csrf();
        MvcResult administrator = signIn(csrf, "bootstrap-29@example.com", "bootstrap administrator password", "198.51.100.134")
                .andExpect(status().isOk())
                .andReturn();
        inviteAndActivate(csrf, administrator, "moderator-29b@example.com", "MODERATOR");
        MvcResult moderator = signIn(csrf, "moderator-29b@example.com", "moderator activation password", "198.51.100.135")
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/v1/admin/operational-accounts")
                        .cookie(sessionCookie(moderator)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/a-future-trading-command")
                        .cookie(sessionCookie(moderator), csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void concurrentAdministratorDeactivationsRetainOneActiveAdministrator() throws Exception {
        Csrf csrf = csrf();
        MvcResult administrator = signIn(csrf, "bootstrap-29@example.com", "bootstrap administrator password", "198.51.100.138")
                .andExpect(status().isOk())
                .andReturn();
        String firstAdministratorId = inviteAndActivate(csrf, administrator, "administrator-29a@example.com", "ADMINISTRATOR");
        String secondAdministratorId = inviteAndActivate(csrf, administrator, "administrator-29b@example.com", "ADMINISTRATOR");
        Cookie administratorSession = sessionCookie(administrator);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> deactivate(csrf, administratorSession, firstAdministratorId, "Remove first administrator"));
            Future<Integer> second = executor.submit(() -> deactivate(csrf, administratorSession, secondAdministratorId, "Remove second administrator"));
            assertThat(java.util.List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(204, 204);
        }

        mockMvc.perform(get("/api/v1/admin/operational-accounts")
                        .cookie(csrf.cookie(), administratorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role == 'ADMINISTRATOR' && @.status == 'ACTIVE')]")
                        .value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void consumesAnOperationalInvitationExactlyOnceWhenActivationIsRetriedConcurrently() throws Exception {
        Csrf csrf = csrf();
        MvcResult administrator = signIn(csrf, "bootstrap-29@example.com", "bootstrap administrator password", "198.51.100.136")
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(post("/api/v1/admin/operational-accounts")
                        .cookie(csrf.cookie(), sessionCookie(administrator))
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"concurrent-operational-29@example.com","role":"MODERATOR",
                                 "reasonCategory":"STAFFING","publicReason":"Concurrency coverage"}
                                """))
                .andExpect(status().isCreated());
        String token = activationToken(mailSender.awaitMessage().getContent().toString());

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> activate(csrf, token));
            Future<Integer> second = executor.submit(() -> activate(csrf, token));
            assertThat(java.util.List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(200, 400);
        }
    }

    private String inviteAndActivate(Csrf csrf, MvcResult administrator, String email, String role) throws Exception {
        invite(csrf, administrator, email, role);
        String token = activationToken(mailSender.awaitMessage().getContent().toString());
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/activate-operational-account")
                                .cookie(csrf.cookie())
                                .header("X-XSRF-TOKEN", csrf.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"token":"%s","password":"%s"}
                                        """.formatted(token, role.equals("MODERATOR")
                                        ? "moderator activation password"
                                        : "administrator activation password")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
    }

    private String invite(Csrf csrf, MvcResult administrator, String email, String role) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/operational-accounts")
                        .cookie(csrf.cookie(), sessionCookie(administrator))
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "role": "%s",
                                  "reasonCategory": "STAFFING",
                                  "publicReason": "Support the operations team"
                                }
                                """.formatted(email, role)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .get("id")
                .asText();
    }

    private org.springframework.test.web.servlet.ResultActions activateWithStatus(Csrf csrf, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/activate-operational-account")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","password":"activation password"}
                        """.formatted(token)));
    }

    private int deactivate(Csrf csrf, Cookie administratorSession, String accountId, String publicReason) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/operational-accounts/" + accountId + "/deactivate")
                        .cookie(csrf.cookie(), administratorSession)
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCategory":"SECURITY","publicReason":"%s"}
                                """.formatted(publicReason)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int activate(Csrf csrf, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/activate-operational-account")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"concurrent activation password"}
                                """.formatted(token)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions signIn(
            Csrf csrf, String email, String password, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/sign-in")
                .cookie(csrf.cookie())
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                })
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return new Csrf(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText(),
                result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private String activationToken(String body) {
        Matcher matcher = ACTIVATION_TOKEN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private Cookie sessionCookie(MvcResult result) {
        return java.util.Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sign-in did not issue a session cookie."));
    }

    private record Csrf(String token, Cookie cookie) {
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

        @Override
        public void send(MimeMessage message) {
            messages.add(message);
        }

        MimeMessage awaitMessage() throws InterruptedException {
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (messages.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            assertThat(messages).isNotEmpty();
            return messages.getLast();
        }

        void clear() {
            messages.clear();
        }
    }
}
