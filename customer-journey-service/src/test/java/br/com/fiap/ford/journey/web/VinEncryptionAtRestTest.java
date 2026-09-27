package br.com.fiap.ford.journey.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.ford.journey.application.IntelligencePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class VinEncryptionAtRestTest {
    private static final String VIN = "1FMCU9GDXMUA55555";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean IntelligencePort intelligence;

    @Test
    void storesVinEncryptedWhileApiReturnsPlaintextToAuthorizedUser() throws Exception {
        String admin = mapper.readTree(mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin@ford.com\",\"password\":\"Ford@123\"}"))
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        String created = mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"vin\":\"" + VIN + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(created).get("id").asText());

        Map<String, Object> row = jdbc.queryForMap("select vin, vin_hash from customer_journeys where id = ?", id);
        assertThat((String) row.get("vin")).startsWith("v1:").doesNotContain(VIN);
        assertThat((String) row.get("vin_hash")).hasSize(64).doesNotContain(VIN);

        mvc.perform(get("/api/v1/journeys/{id}", id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vin").value(VIN));
        mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"vin\":\"" + VIN + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
