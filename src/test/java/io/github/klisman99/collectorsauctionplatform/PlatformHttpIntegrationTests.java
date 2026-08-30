package io.github.klisman99.collectorsauctionplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformHttpIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsPublicPlatformStatusWithATraceId() throws Exception {
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.service").value("Collectors Auction Platform"))
                .andExpect(jsonPath("$.status").value("operational"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void exposesHealthForLocalOrchestrators() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void exposesACsrfTokenForFutureSessionBackedCommands() throws Exception {
        mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void generatesAnOpenApiDocumentFromTheBackendContract() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.1"))
                .andExpect(jsonPath("$.paths['/api/v1/status'].get.operationId").value("getPlatformStatus"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(document).contains("PlatformStatus");
    }
}
