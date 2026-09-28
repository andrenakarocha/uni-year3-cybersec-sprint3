package br.com.fiap.ford.journey.infra;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/** Cifra o atributo ao gravar e decifra ao ler: o domínio nunca vê o valor cifrado. */
@Converter
@Component
class EncryptedStringConverter implements AttributeConverter<String, String> {
    private final FieldEncryption encryption;

    EncryptedStringConverter(FieldEncryption encryption) {
        this.encryption = encryption;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : encryption.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String column) {
        return column == null ? null : encryption.decrypt(column);
    }
}
