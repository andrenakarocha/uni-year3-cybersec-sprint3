package br.com.fiap.ford.journey.infra;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Criptografia de campo para dados pessoais em repouso (evolução do EncryptedFieldConverter da
 * Sprint 1).
 *
 * <ul>
 *   <li>AES-256-GCM: cifra autenticada, adulteração do valor no banco é detectada na leitura.</li>
 *   <li>IV aleatório de 96 bits por valor (NIST SP 800-38D): o mesmo VIN nunca gera o mesmo texto.</li>
 *   <li>Formato {@code v1:base64(iv || ciphertext || tag)}: o prefixo de versão permite rotacionar
 *       a chave, decifrando v1 e regravando em v2.</li>
 *   <li>Blind index HMAC-SHA256: busca por igualdade e índice único sem decifrar a tabela.</li>
 * </ul>
 *
 * A chave mestra (32 bytes aleatórios, FIELD_ENCRYPTION_KEY) não é usada direto: duas subchaves
 * são derivadas por HMAC com rótulos distintos, para a mesma chave não servir a dois algoritmos.
 */
@Component
public class FieldEncryption {
    private static final String VERSION = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey encryptionKey;
    private final SecretKey indexKey;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryption(@Value("${security.field-encryption.key}") String base64Key) {
        byte[] master = Base64.getDecoder().decode(base64Key);
        if (master.length != 32) {
            throw new IllegalStateException("security.field-encryption.key must be 32 random bytes in base64");
        }
        this.encryptionKey = new SecretKeySpec(derive(master, "ford-zero-touch/field-encryption/v1"), "AES");
        this.indexKey = new SecretKeySpec(derive(master, "ford-zero-touch/blind-index/v1"), "HmacSHA256");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            return VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("field encryption failed", error);
        }
    }

    public String decrypt(String stored) {
        if (!stored.startsWith(VERSION)) {
            throw new IllegalStateException("unsupported encrypted field version");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("field decryption failed: value tampered or wrong key", error);
        }
    }

    public String blindIndex(String plaintext) {
        return HexFormat.of().formatHex(hmac(indexKey, plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] derive(byte[] master, String label) {
        return hmac(new SecretKeySpec(master, "HmacSHA256"), label.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(SecretKey key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC unavailable", error);
        }
    }
}
