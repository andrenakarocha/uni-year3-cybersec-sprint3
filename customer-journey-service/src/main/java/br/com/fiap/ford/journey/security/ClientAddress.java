package br.com.fiap.ford.journey.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP de origem para a trilha de segurança. X-Real-IP é sobrescrito pelo gateway e só é confiável
 * porque os serviços não publicam porta: todo tráfego chega pelo Nginx (compose.yml).
 */
public final class ClientAddress {
    private ClientAddress() {}

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Real-IP");
        return forwarded != null && !forwarded.isBlank() ? forwarded : request.getRemoteAddr();
    }
}
