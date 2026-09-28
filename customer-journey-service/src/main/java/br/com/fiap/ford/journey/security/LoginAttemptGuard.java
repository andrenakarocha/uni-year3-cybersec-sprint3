package br.com.fiap.ford.journey.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bloqueio temporário de conta após falhas consecutivas de login (anti força bruta por usuário).
 * Complementa o rate limit por IP do gateway, que não impede um ataque distribuído contra uma
 * única conta. Vale também para usuários inexistentes, para não revelar quais contas existem.
 * Em memória nesta POC; com várias réplicas o estado iria para o Redis já presente na stack.
 */
@Component
public class LoginAttemptGuard {
    private static final int MAX_TRACKED_USERS = 10_000;

    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public LoginAttemptGuard(@Value("${security.login.max-failures:5}") int maxFailures,
            @Value("${security.login.lock-duration:PT15M}") Duration lockDuration, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        Failures entry = failures.get(username);
        return entry != null && entry.count() >= maxFailures && !expired(entry, clock.instant());
    }

    public void recordFailure(String username) {
        Instant now = clock.instant();
        if (failures.size() >= MAX_TRACKED_USERS) {
            failures.values().removeIf(entry -> expired(entry, now));
        }
        failures.compute(username, (key, entry) -> entry == null || expired(entry, now)
                ? new Failures(1, now) : new Failures(entry.count() + 1, now));
    }

    public void reset(String username) {
        failures.remove(username);
    }

    private boolean expired(Failures entry, Instant now) {
        return !now.isBefore(entry.lastFailure().plus(lockDuration));
    }

    private record Failures(int count, Instant lastFailure) {}
}
