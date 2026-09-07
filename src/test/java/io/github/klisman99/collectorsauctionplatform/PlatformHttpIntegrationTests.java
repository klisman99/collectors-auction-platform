package io.github.klisman99.collectorsauctionplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PlatformHttpIntegrationTests.TestEndpoints.class)
class PlatformHttpIntegrationTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private Tracer tracer;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void reportsPublicPlatformStatusWithATraceId() throws Exception {
    mockMvc
        .perform(get("/api/v1/status"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Trace-Id"))
        .andExpect(jsonPath("$.service").value("Collectors Auction Platform"))
        .andExpect(jsonPath("$.status").value("operational"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  void exposesHealthForLocalOrchestrators() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void exposesACsrfTokenForFutureSessionBackedCommands() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/v1/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(cookie().exists("JSESSIONID"))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andReturn();

    assertThat(result.getResponse().getCookie("JSESSIONID")).isNotNull();
  }

  @Test
  void generatesAnOpenApiDocumentFromTheBackendContract() throws Exception {
    String document =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").isNotEmpty())
            .andExpect(
                jsonPath("$.paths['/api/v1/status'].get.operationId").value("getPlatformStatus"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(document).contains("PlatformStatus");
  }

  @Test
  void configuresOpenTelemetryTracing() {
    assertThat(tracer).isNotNull();
  }

  @Test
  void reportsValidationFailuresAsStableProblemDetails() throws Exception {
    CsrfSession csrfSession = csrfSession();
    mockMvc
        .perform(
            post("/api/v1/test/validation")
                .cookie(csrfSession.cookie())
                .header("X-XSRF-TOKEN", csrfSession.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("value"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void reportsMalformedJsonAsStableProblemDetails() throws Exception {
    CsrfSession csrfSession = csrfSession();
    mockMvc
        .perform(
            post("/api/v1/test/validation")
                .cookie(csrfSession.cookie())
                .header("X-XSRF-TOKEN", csrfSession.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REQUEST_BODY_UNREADABLE"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void reportsMissingResourcesAsStableProblemDetails() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andReturn();

    String responseTraceId = result.getResponse().getHeader("X-Trace-Id");
    String problemTraceId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("traceId").asText();
    assertThat(responseTraceId).matches("[0-9a-f]{32}").isEqualTo(problemTraceId);
  }

  @Test
  void reportsUnexpectedFailuresWithoutLeakingTheirMessage() throws Exception {
    mockMvc
        .perform(get("/api/v1/test/failure"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail").value("The request could not be completed."))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  @Test
  void reportsSecurityRejectionsAsStableProblemDetails() throws Exception {
    mockMvc
        .perform(get("/blocked"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  private CsrfSession csrfSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn();
    String token =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    return new CsrfSession(token, result.getResponse().getCookie("XSRF-TOKEN"));
  }

  @RestController
  static class TestEndpoints {

    @PostMapping("/api/v1/test/validation")
    Map<String, String> validate(@Valid @RequestBody TestRequest request) {
      return Map.of("value", request.value());
    }

    @GetMapping("/api/v1/test/failure")
    void fail() {
      throw new IllegalStateException("secret implementation detail");
    }
  }

  record TestRequest(@NotBlank String value) {}

  record CsrfSession(String token, Cookie cookie) {}
}
