package br.com.fiap.ford.journey.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Autorização em nível de objeto (OWASP API1:2023 BOLA). O papel diz O QUE o usuário pode fazer;
 * a posse diz SOBRE QUAL registro. Equipe (ADVISER, ADMIN) atende qualquer cliente; CUSTOMER só
 * enxerga o que pertence ao customer_id do próprio token.
 */
public final class Ownership {
    private static final Set<String> STAFF = Set.of("ADVISER", "ADMIN");

    private Ownership() {}

    public static boolean canReadCustomerData(Jwt principal, UUID ownerId) {
        List<String> roles = principal.getClaimAsStringList("roles");
        if (roles != null && roles.stream().anyMatch(STAFF::contains)) {
            return true;
        }
        return ownerId != null && ownerId.toString().equals(principal.getClaimAsString("customer_id"));
    }
}
