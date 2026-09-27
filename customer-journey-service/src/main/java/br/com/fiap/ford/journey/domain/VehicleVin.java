package br.com.fiap.ford.journey.domain;

import java.util.Locale;

public record VehicleVin(String value) {
    public VehicleVin {
        if (value == null || !value.toUpperCase(Locale.ROOT).matches("^[A-HJ-NPR-Z0-9]{17}$")) {
            throw new DomainException("VIN must contain 17 valid characters");
        }
        value = value.toUpperCase(Locale.ROOT);
    }
}

