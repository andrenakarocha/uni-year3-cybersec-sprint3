package br.com.fiap.ford.journey.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Trilha de segurança estruturada (evoluída do AuditLogger da Sprint 1). Logger dedicado
 * "security.audit" para o coletor rotear ao SIEM separado do log de aplicação. Cada evento sai
 * como JSON (logstash) com campos fixos no estilo ECS; o request_id vem do MDC. E-mails vão
 * mascarados (minimização da LGPD) e tokens nunca são registrados.
 */
@Component
public class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger("security.audit");

    public void loginSucceeded(String username, String clientIp) {
        auth(log.atInfo(), "auth.login.success", "success", username, clientIp).log("login succeeded");
    }

    public void loginFailed(String username, String clientIp) {
        auth(log.atWarn(), "auth.login.failure", "failure", username, clientIp).log("login failed");
    }

    public void loginBlocked(String username, String clientIp) {
        auth(log.atWarn(), "auth.login.blocked", "denied", username, clientIp)
                .log("login blocked after repeated failures");
    }

    public void tokenRejected(String method, String path, String clientIp, String reason) {
        log.atWarn()
                .addKeyValue("event.category", "authentication")
                .addKeyValue("event.action", "auth.token.rejected")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.reason", reason)
                .addKeyValue("http.request.method", method)
                .addKeyValue("url.path", path)
                .addKeyValue("source.ip", clientIp)
                .log("request rejected: missing or invalid bearer token");
    }

    public void accessDenied(String method, String path, String clientIp) {
        log.atWarn()
                .addKeyValue("event.category", "authorization")
                .addKeyValue("event.action", "authz.denied")
                .addKeyValue("event.outcome", "denied")
                .addKeyValue("user.name", mask(currentUser()))
                .addKeyValue("http.request.method", method)
                .addKeyValue("url.path", path)
                .addKeyValue("source.ip", clientIp)
                .log("access denied: insufficient role");
    }

    public void criticalChange(String action, String resourceType, Object resourceId, String detail) {
        log.atInfo()
                .addKeyValue("event.category", "configuration")
                .addKeyValue("event.action", action)
                .addKeyValue("event.outcome", "success")
                .addKeyValue("user.name", mask(currentUser()))
                .addKeyValue("resource.type", resourceType)
                .addKeyValue("resource.id", String.valueOf(resourceId))
                .addKeyValue("change.detail", detail)
                .log("critical change");
    }

    private LoggingEventBuilder auth(LoggingEventBuilder builder, String action, String outcome,
            String username, String clientIp) {
        return builder
                .addKeyValue("event.category", "authentication")
                .addKeyValue("event.action", action)
                .addKeyValue("event.outcome", outcome)
                .addKeyValue("user.name", mask(username))
                .addKeyValue("source.ip", clientIp);
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }
}
