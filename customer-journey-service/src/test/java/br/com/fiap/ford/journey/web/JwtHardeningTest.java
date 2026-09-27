package br.com.fiap.ford.journey.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.ford.journey.application.IntelligencePort;
import br.com.fiap.ford.journey.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "security.jwt.ttl=PT15M")
@AutoConfigureMockMvc
class JwtHardeningTest {
    @Autowired MockMvc mvc;
    @MockitoBean IntelligencePort intelligence;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired TokenService tokens;

    @Test
    void issuedTokensAreShortLivedAndCarryUniqueId() {
        Jwt first = decoder.decode(tokens.issue("admin@ford.com", Set.of("ADMIN"), Instant.now()).accessToken());
        Jwt second = decoder.decode(tokens.issue("admin@ford.com", Set.of("ADMIN"), Instant.now()).accessToken());

        assertThat(Duration.between(first.getIssuedAt(), first.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(first.getId()).isNotBlank().isNotEqualTo(second.getId());
    }

    @Test
    void rejectsValidlySignedTokenWithoutExpiration() throws Exception {
        JwtClaimsSet neverExpires = JwtClaimsSet.builder()
                .issuer("ford-zero-touch")
                .audience(List.of("ford-api"))
                .subject("attacker")
                .issuedAt(Instant.now())
                .claim("roles", List.of("ADMIN"))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), neverExpires)).getTokenValue();

        mvc.perform(get("/api/v1/journeys/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
