package br.com.fiap.ford.journey.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.ford.journey.application.IntelligencePort;
import br.com.fiap.ford.journey.security.DemoUserDirectory;
import br.com.fiap.ford.journey.security.SecurityAuditLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** OWASP API1:2023 — um cliente não lê a jornada de outro trocando o ID na URL. */
@SpringBootTest
@AutoConfigureMockMvc
class ObjectLevelAuthorizationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtDecoder decoder;
    @MockitoBean IntelligencePort intelligence;
    @MockitoSpyBean SecurityAuditLogger audit;

    @Test
    void customerCannotReadAnotherCustomersJourney() throws Exception {
        UUID foreign = createJourney(UUID.randomUUID(), "1FMCU9GDXMUA70001");

        mvc.perform(get("/api/v1/journeys/{id}", foreign).header("Authorization", "Bearer " + token("customer@ford.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verify(audit).objectAccessDenied(eq("customer_journey"), eq(foreign), anyString(), anyString());

        mvc.perform(get("/api/v1/journeys/{id}", foreign).header("Authorization", "Bearer " + token("adviser@ford.com")))
                .andExpect(status().isOk());
    }

    @Test
    void customerReadsOwnJourneyAndTokenCarriesOwnershipClaims() throws Exception {
        UUID own = createJourney(DemoUserDirectory.DEMO_CUSTOMER_ID, "1FMCU9GDXMUA70002");
        String customer = token("customer@ford.com");

        mvc.perform(get("/api/v1/journeys/{id}", own).header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(DemoUserDirectory.DEMO_CUSTOMER_ID.toString()));
        Jwt jwt = decoder.decode(customer);
        assertThat(jwt.getClaimAsString("customer_id")).isEqualTo(DemoUserDirectory.DEMO_CUSTOMER_ID.toString());
        assertThat(jwt.getClaimAsStringList("vins")).contains("1FMCU9GDXMUA70002");
        assertThat(decoder.decode(token("adviser@ford.com")).hasClaim("customer_id")).isFalse();
    }

    private UUID createJourney(UUID customerId, String vin) throws Exception {
        String body = mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + token("admin@ford.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId + "\",\"vin\":\"" + vin + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(body).get("id").asText());
    }

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"Ford@123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }
}
