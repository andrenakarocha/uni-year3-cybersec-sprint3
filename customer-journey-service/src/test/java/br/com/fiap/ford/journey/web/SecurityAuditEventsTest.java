package br.com.fiap.ford.journey.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.ford.journey.application.IntelligencePort;
import br.com.fiap.ford.journey.security.SecurityAuditLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuditEventsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean IntelligencePort intelligence;
    @MockitoSpyBean SecurityAuditLogger audit;

    @Test
    void missingTokenIsAudited() throws Exception {
        mvc.perform(get("/api/v1/journeys/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        verify(audit).tokenRejected(eq("GET"), anyString(), anyString(), anyString());
    }

    @Test
    void roleDenialIsAudited() throws Exception {
        mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + token("customer@ford.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"vin\":\"1FMCU9GDXMUA44444\"}"))
                .andExpect(status().isForbidden());
        verify(audit).accessDenied(eq("POST"), eq("/api/v1/journeys"), anyString());
    }

    @Test
    void journeyCreationIsAuditedAsCriticalChange() throws Exception {
        mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + token("adviser@ford.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"vin\":\"1FMCU9GDXMUA33333\"}"))
                .andExpect(status().isCreated());
        verify(audit).criticalChange(eq("journey.created"), eq("customer_journey"), any(), eq("status=DETECTED"));
    }

    @Test
    void requestIdIsEchoedOnlyWhenSafe() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-ID", "gw-5f2c9a1e-trace"))
                .andExpect(header().string("X-Request-ID", "gw-5f2c9a1e-trace"));
        String replaced = mvc.perform(get("/actuator/health").header("X-Request-ID", "forged\nlevel=ERROR"))
                .andReturn().getResponse().getHeader("X-Request-ID");
        assertThat(replaced).doesNotContain("forged").hasSize(36);
    }

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"Ford@123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }
}
