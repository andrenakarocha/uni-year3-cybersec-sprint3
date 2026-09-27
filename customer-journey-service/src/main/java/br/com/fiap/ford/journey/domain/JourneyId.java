package br.com.fiap.ford.journey.domain;

import java.util.UUID;

public record JourneyId(UUID value) {
    public JourneyId {
        if (value == null) throw new DomainException("journey id is required");
    }

    public static JourneyId newId() {
        return new JourneyId(UUID.randomUUID());
    }
}

