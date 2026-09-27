package br.com.fiap.ford.journey.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, @Value("${security.jwt.ttl}") Duration ttl) {
        this.encoder = encoder;
        this.ttl = ttl;
    }

    public Token issue(String subject, Set<String> roles, Instant now) {
        return issue(subject, roles, null, Set.of(), now);
    }

    /** customer_id e vins são os claims de posse que os três serviços usam contra BOLA. */
    public Token issue(String subject, Set<String> roles, UUID customerId, Set<String> vins, Instant now) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("ford-zero-touch")
                .audience(java.util.List.of("ford-api"))
                .subject(subject)
                .id(UUID.randomUUID().toString()) // jti: rastreia o token nos logs e permite revogação pontual
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", roles);
        if (customerId != null) {
            claims.claim("customer_id", customerId.toString()).claim("vins", vins);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new Token(
                encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue(), ttl.toSeconds());
    }

    public record Token(String accessToken, long expiresIn) {}
}
