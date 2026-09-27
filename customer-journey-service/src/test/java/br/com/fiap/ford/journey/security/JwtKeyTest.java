package br.com.fiap.ford.journey.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtKeyTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void rejectsSecretShorterThan256Bits() {
        assertThatThrownBy(() -> config.jwtKey("short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void acceptsSecretWith256Bits() {
        assertThat(config.jwtKey("0123456789abcdef0123456789abcdef").getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
