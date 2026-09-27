package br.com.fiap.ford.journey.web;

import br.com.fiap.ford.journey.security.DemoUserDirectory;
import br.com.fiap.ford.journey.security.TokenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
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

    AuthController(DemoUserDirectory users, TokenService tokens, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.clock = clock;
    }

    @PostMapping("/token")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    TokenService.Token token(@Valid @RequestBody LoginRequest request) {
        var user = users.authenticate(request.username(), request.password())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        return tokens.issue(user.username(), user.roles(), clock.instant());
    }

    record LoginRequest(@Email @NotBlank String username, @NotBlank String password) {}
}
