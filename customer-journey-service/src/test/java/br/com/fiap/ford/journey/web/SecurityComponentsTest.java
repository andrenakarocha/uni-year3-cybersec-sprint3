package br.com.fiap.ford.journey.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fiap.ford.journey.security.DemoUserDirectory;
import br.com.fiap.ford.journey.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class SecurityComponentsTest {
    @Test
    void directoryAuthenticatesKnownUsersCaseInsensitively() {
        DemoUserDirectory directory = new DemoUserDirectory(new BCryptPasswordEncoder(4));
        assertThat(directory.authenticate("ADMIN@FORD.COM", "Ford@123")).isPresent();
        assertThat(directory.authenticate("technician@ford.com", "Ford@123").orElseThrow().roles())
                .containsExactly("TECHNICIAN");
        assertThat(directory.authenticate("missing@ford.com", "Ford@123")).isEmpty();
        assertThat(directory.authenticate("admin@ford.com", "wrong")).isEmpty();
    }

    @Test
    void tokenServiceReturnsEncodedTokenAndTtl() {
        JwtEncoder encoder = new JwtEncoder() {
            @Override
            public Jwt encode(JwtEncoderParameters parameters) {
                Instant issued = parameters.getClaims().getIssuedAt();
                return new Jwt("encoded", issued, parameters.getClaims().getExpiresAt(),
                        java.util.Map.of("alg", "HS256"), parameters.getClaims().getClaims());
            }
        };
        TokenService service = new TokenService(encoder, Duration.ofMinutes(30));
        var token = service.issue("user", Set.of("ADMIN"), Instant.parse("2026-09-26T12:00:00Z"));
        assertThat(token.accessToken()).isEqualTo("encoded");
        assertThat(token.expiresIn()).isEqualTo(1800);
    }
}
