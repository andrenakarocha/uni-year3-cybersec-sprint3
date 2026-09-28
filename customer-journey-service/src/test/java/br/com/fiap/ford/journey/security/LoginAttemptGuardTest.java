package br.com.fiap.ford.journey.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptGuardTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-27T20:00:00Z"));
    private final LoginAttemptGuard guard = new LoginAttemptGuard(3, Duration.ofMinutes(15), clock);

    @Test
    void locksAtThresholdAndUnlocksAfterLockDuration() {
        guard.recordFailure("admin@ford.com");
        guard.recordFailure("admin@ford.com");
        assertThat(guard.isLocked("admin@ford.com")).isFalse();
        guard.recordFailure("admin@ford.com");
        assertThat(guard.isLocked("admin@ford.com")).isTrue();

        clock.advance(Duration.ofMinutes(15));
        assertThat(guard.isLocked("admin@ford.com")).isFalse();
        guard.recordFailure("admin@ford.com");
        assertThat(guard.isLocked("admin@ford.com")).isFalse();
    }

    @Test
    void masksEmailInAuditTrail() {
        assertThat(SecurityAuditLogger.mask("customer@ford.com")).isEqualTo("cu***@ford.com");
        assertThat(SecurityAuditLogger.mask("not-an-email")).isEqualTo("***");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
