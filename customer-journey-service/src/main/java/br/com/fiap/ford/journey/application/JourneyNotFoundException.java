package br.com.fiap.ford.journey.application;

import java.util.UUID;

public class JourneyNotFoundException extends RuntimeException {
    public JourneyNotFoundException(UUID id) {
        super("journey not found: " + id);
    }
}

