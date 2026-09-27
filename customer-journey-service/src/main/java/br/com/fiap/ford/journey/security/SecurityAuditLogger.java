package br.com.fiap.ford.journey.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/**
 * Trilha de segurança estruturada (evoluída do AuditLogger da Sprint 1). Logger dedicado
 * "security.audit" para o coletor rotear ao SIEM separado do log de aplicação. Cada evento sai
 * como JSON (logstash) com campos fixos, e o e-mail vai mascarado (minimização da LGPD).
 */
@Component
public class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger("security.audit");

    public void loginSucceeded(String username, String clientIp) {
        event(log.atInfo(), "auth.login.success", "success", username, clientIp).log("login succeeded");
    }

    public void loginFailed(String username, String clientIp) {
        event(log.atWarn(), "auth.login.failure", "failure", username, clientIp).log("login failed");
    }

    public void loginBlocked(String username, String clientIp) {
        event(log.atWarn(), "auth.login.blocked", "denied", username, clientIp)
                .log("login blocked after repeated failures");
    }

    private LoggingEventBuilder event(LoggingEventBuilder builder, String action, String outcome,
            String username, String clientIp) {
        return builder
                .addKeyValue("event.category", "authentication")
                .addKeyValue("event.action", action)
                .addKeyValue("event.outcome", outcome)
                .addKeyValue("user.name", mask(username))
                .addKeyValue("source.ip", clientIp);
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }
}
