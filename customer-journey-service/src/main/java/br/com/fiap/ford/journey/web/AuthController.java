package br.com.fiap.ford.journey.web;

import br.com.fiap.ford.journey.security.ClientAddress;
import br.com.fiap.ford.journey.security.DemoUserDirectory;
import br.com.fiap.ford.journey.security.LoginAttemptGuard;
import br.com.fiap.ford.journey.security.SecurityAuditLogger;
import br.com.fiap.ford.journey.security.TokenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    private final DemoUserDirectory users;
    private final TokenService tokens;
    private final Clock clock;
    private final LoginAttemptGuard attempts;
    private final SecurityAuditLogger audit;

    AuthController(DemoUserDirectory users, TokenService tokens, Clock clock, LoginAttemptGuard attempts,
            SecurityAuditLogger audit) {
        this.users = users;
        this.tokens = tokens;
        this.clock = clock;
        this.attempts = attempts;
        this.audit = audit;
    }

    @PostMapping("/token")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    TokenService.Token token(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String username = request.username().toLowerCase(Locale.ROOT);
        String clientIp = ClientAddress.of(http);
        if (attempts.isLocked(username)) {
            audit.loginBlocked(username, clientIp);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "too many failed attempts, try again later");
        }
        var user = users.authenticate(username, request.password());
        if (user.isEmpty()) {
            attempts.recordFailure(username);
            audit.loginFailed(username, clientIp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }
        attempts.reset(username);
        audit.loginSucceeded(username, clientIp);
        return tokens.issue(user.get().username(), user.get().roles(), clock.instant());
    }

    record LoginRequest(@Email @NotBlank String username, @NotBlank String password) {}
}
