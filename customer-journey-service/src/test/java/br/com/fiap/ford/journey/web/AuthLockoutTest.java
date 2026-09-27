package br.com.fiap.ford.journey.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.ford.journey.application.IntelligencePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AuthLockoutTest {
    @Autowired MockMvc mvc;
    @MockitoBean IntelligencePort intelligence;

    @Test
    void locksAccountAfterFiveFailuresEvenWithCorrectPassword() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            login("technician@ford.com", "wrong").andExpect(status().isUnauthorized());
        }
        login("technician@ford.com", "Ford@123")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("too many failed attempts, try again later"));
    }

    @Test
    void locksUnknownUsersTheSameWayToAvoidAccountEnumeration() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            login("nobody@ford.com", "guess-" + attempt).andExpect(status().isUnauthorized());
        }
        login("nobody@ford.com", "guess").andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            login("vehicle@ford.com", "wrong").andExpect(status().isUnauthorized());
        }
        login("vehicle@ford.com", "Ford@123").andExpect(status().isCreated());
        for (int attempt = 0; attempt < 4; attempt++) {
            login("vehicle@ford.com", "wrong").andExpect(status().isUnauthorized());
        }
        login("vehicle@ford.com", "Ford@123").andExpect(status().isCreated());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }
}
