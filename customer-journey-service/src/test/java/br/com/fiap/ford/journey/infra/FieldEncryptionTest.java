package br.com.fiap.ford.journey.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class FieldEncryptionTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final FieldEncryption encryption = new FieldEncryption(KEY);

    @Test
    void roundTripsWithRandomIvAndVersionPrefix() {
        String first = encryption.encrypt("1FMCU9GDXMUA12345");
        String second = encryption.encrypt("1FMCU9GDXMUA12345");

        assertThat(first).startsWith("v1:").doesNotContain("1FMCU9GDXMUA12345").isNotEqualTo(second);
        assertThat(encryption.decrypt(first)).isEqualTo("1FMCU9GDXMUA12345");
        assertThat(encryption.decrypt(second)).isEqualTo("1FMCU9GDXMUA12345");
    }

    @Test
    void detectsTamperingThroughGcmTag() {
        String stored = encryption.encrypt("1FMCU9GDXMUA12345");
        byte[] payload = Base64.getDecoder().decode(stored.substring(3));
        payload[payload.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> encryption.decrypt(tampered)).hasMessageContaining("tampered or wrong key");
    }

    @Test
    void rejectsValueEncryptedWithAnotherKey() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        String foreign = new FieldEncryption(Base64.getEncoder().encodeToString(otherKey)).encrypt("1FMCU9GDXMUA12345");

        assertThatThrownBy(() -> encryption.decrypt(foreign)).hasMessageContaining("tampered or wrong key");
    }

    @Test
    void blindIndexIsDeterministicAndDoesNotExposeTheValue() {
        String index = encryption.blindIndex("1FMCU9GDXMUA12345");

        assertThat(index).hasSize(64).isEqualTo(encryption.blindIndex("1FMCU9GDXMUA12345"))
                .isNotEqualTo(encryption.blindIndex("1FMCU9GDXMUA99999"));
    }

    @Test
    void requiresA256BitKey() {
        assertThatThrownBy(() -> new FieldEncryption(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
